/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.impl.NucleusAdapterIpcClientImpl;
import picocli.CommandLine;
import java.io.Reader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.RuntimeConstants;

@CommandLine.Command(name = "quick", resourceBundle = "com.aws.greengrass.cli.CLI_messages")
public class TemplateCommand extends BaseCommand {

    @CommandLine.Option(names = {"-dr", "--dryrun"},
            paramLabel = "Dry run: don't do any actual work")
    private boolean dryrun;

    @CommandLine.Option(names = "-gtd",
            paramLabel = "Generated Template Directory",
            defaultValue = "~/gg2Templates")
    private String generatedTemplateDirectory;

    @CommandLine.Option(names = {"-g", "--groupId"},
            paramLabel = "Group ID")
    private String group = null;

    @CommandLine.Parameters(paramLabel = "Files for template generation")
    private String[] files;

    private String recipeDir;
    private String artifactDir;
    private Path genTemplateDir;
    private final ArrayList<String> params = new ArrayList<>();
    private Path localTemplateDir;
    private String keyFile = null;
    private String serviceName = null;
    private String serviceVersion = null;
    private boolean generateRecipe = true;
    private final List<String> artifacts = new ArrayList<>();
    private String hashbang;
    private String javaVersion = "11";
    private final Map<String, byte[]> auxRecipies = new LinkedHashMap<>();
    private boolean isOK = true;

    @Override
    public void run() {
        // This is nuts, but apparently necessary.
        if (template() != 0) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Template generation failed");
        }
    }

    int template() {
        genTemplateDir = Paths.get(NucleusAdapterIpcClientImpl.deTilde(generatedTemplateDirectory));
        if (files == null || files.length == 0) {
            err("cli.tpl.files");
        }
        if (files != null) {
            scanFiles();
            if (!build()) {
                return 1;
            }
        }
        if (!isOK) {
            return 1;
        }
        if (dryrun) {
            return 0;
        }
        ArrayList<String> args = new ArrayList<>();
        args.add("--ggcRootPath");
        args.add(parent.getGgcRootPath());
        args.add("deployment");
        args.add("create");
        args.add("-r");
        args.add(recipeDir);
        args.add("-a");
        args.add(artifactDir);
        args.add("-m");
        args.add(serviceName + "=" + serviceVersion);
        if (!isEmpty(group)) {
            args.add("-g");
            args.add(group);
        }
        params.forEach(s -> {
            args.add("-p");
            args.add(s);
        });
//        args.add("-c");  ??
        System.out.print("Executing: greengrass-cli");
        args.forEach(s -> System.out.print(' ' + s));
        System.out.println();
        return parent.runCommand(args.toArray(new String[args.size()]));
    }

    private int scanFiles() {
        for (String fn : files) {
            int eq = fn.indexOf('=');
            if (eq > 0) {
                // Assume that any argument with an = sign is a parameter.
                params.add(fn);
            } else {
                Path pn = Paths.get(fn);
                switch (extension(fn)) {
                    default:
                        addArtifact(fn, true);
                        break;
                    case "jar":
                        if (serviceName == null) {
                            if (!harvestJar(pn)) {
                                isOK = false;
                            }
                        }
                        addArtifact(fn, false);
                        break;
//                    case "json": TODO
                    case "yaml":
                    case "yml":
                    case "gg2r": {
                        byte[] body = capture(pn);
                        addRecipe(fn, body);
                        if (serviceName == null) {
                            extractInfo(new String(body));
                        }
                        generateRecipe = false;
                    }
                    break;
                }
            }
        }
        return 0;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private void addArtifact(String fn, boolean xinfo) {
        if (serviceName == null) {
            serviceName = chopExtension(fn);
            Matcher m = Pattern.compile("(.+)-([0-9]+\\..*)").matcher(serviceName);
            if (m.matches()) {
                serviceName = m.group(1);
                serviceVersion = m.group(2);
            }
        }
        if (keyFile == null) {
            keyFile = fn;
            try {
                if (xinfo) {
                    extractInfo(Paths.get(fn).toUri().toURL());
                }
            } catch (MalformedURLException ex) {
                Logger.getLogger(TemplateCommand.class.getName()).log(Level.SEVERE, null, ex);
                isOK = false;
            }
        }
        artifacts.add(fn);
    }

    private void extractInfo(URL name) {
        if (name != null) {
            extractInfo(readAll(name));
        }
    }

    private void extractInfo(String body) {
        if (body != null) {
            Matcher m = Pattern.compile("#! *(.*)").matcher(body);
            if (m.lookingAt()) {
                hashbang = m.group(1);
            }
            m = Pattern.compile("ComponentName: *([^;,\n\"]+)", Pattern.CASE_INSENSITIVE).matcher(body);
            if (m.find()) {
                serviceName = m.group(1);
            }
            m = Pattern.compile("ComponentVersion: *([^;,\n\"]+)", Pattern.CASE_INSENSITIVE).matcher(body);
            if (m.find()) {
                serviceVersion = m.group(1);
            }
            if (group != null) {
                m = Pattern.compile("ComponentGroup: *([^;,\n\"]+)", Pattern.CASE_INSENSITIVE).matcher(body);
                if (m.find()) {
                    group = m.group(1);
                }
            }
        }
    }

    private String readAll(URL u) {
        if (u != null)
            try (Reader in = new InputStreamReader(new BufferedInputStream(u.openStream()))) {
            StringBuilder sb = new StringBuilder();
            int c;
            int limit = 4000;
            while ((c = in.read()) >= 0 && --limit >= 0) {
                sb.append((char) c);
            }
            return sb.toString();
        } catch (IOException ioe) {
            isOK = false;
        }
        return null;
    }

    @SuppressWarnings("UseSpecificCatch")
    public boolean build() {
        if (genTemplateDir == null) {
            genTemplateDir = Paths.get(System.getProperty("user.home", "/tmp"), "gg2Templates");
        }
        localTemplateDir = genTemplateDir.resolve("templates");
        if (recipeDir != null) {
            if (artifactDir == null) {
                artifactDir = new File(new File(recipeDir).getParentFile(), "artifacts").toString();
            }
        } else if (artifactDir != null) {
            recipeDir = new File(new File(artifactDir).getParentFile(), "recipes").toString();
        } else if (serviceName != null) {
            artifactDir = genTemplateDir.resolve(serviceName).resolve("artifacts").toString();
            recipeDir = genTemplateDir.resolve(serviceName).resolve("recipes").toString();
        }
        if (serviceName == null) {
            serviceName = "Unknown";
        }
        if (serviceVersion == null) {
            serviceVersion = "0.0.0";
        }
        Matcher m = Pattern.compile("(.+)-[A-Z]+").matcher(serviceVersion);
        if (m.matches()) // Clean "-SNAPSHOT" from the end of the version string
        {
            serviceVersion = m.group(1);
        }
        StringWriter tls = new StringWriter();
        if (generateRecipe) {
            final VelocityEngine ve = new VelocityEngine();
            final VelocityContext context = new VelocityContext();
            ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "file,classpath");
            ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
            ve.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, localTemplateDir.toString());
            ve.init();
            String templateName;
            Path keyPath = Paths.get(keyFile);
            if (hashbang != null) {
                templateName = "hashbang.yml";
            } else if (Files.isExecutable(keyPath)) {
                templateName = "executable.yml";
            } else {
                templateName = extension(keyFile) + ".yml";
            }
            context.put("name", serviceName);
            context.put("version", serviceVersion);
            context.put("publisher", System.getProperty("user.name", "Unknown"));
            StringBuilder description = new StringBuilder();
            description.append("Created for ")
                    .append(System.getProperty("user.name"))
                    .append(" on ")
                    .append(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                    .append(" from");
            for (String f : files) {
                description.append(' ').append(f);
            }
            context.put("description", description.toString());
            context.put("file", keyPath.getFileName().toString());
            params.forEach(s -> { // copy params to velocity context
                String[] kv = s.split("=", 1);
                if (kv.length == 2) {
                    context.put(kv[0], kv[1]);
                }
            });
            ArrayList<String> fileArtifactNames = new ArrayList<>();
            artifacts.forEach(fn -> fileArtifactNames.add(new File(fn).getName()));
            context.put("files", artifacts.toArray(new String[fileArtifactNames.size()]));
            context.put("ctx", new opHandlers());
            if (hashbang != null) {
                context.put("hashbang", hashbang);
            }
            if (javaVersion != null) {
                context.put("javaVersion", javaVersion);
            }
            try {
                Template t = ve.getTemplate("templates/" + templateName, "UTF-8");
                t.merge(context, tls);
            } catch (Throwable t) {
                err("cli.tpl.nft", keyFile);
                return false;
            }
        } else {
            System.out.println("[ using provided recipe file ]"); //            ComponentRecipe r = getParsedRecipe();
        }
        Path ad = Paths.get(artifactDir).resolve(serviceName).resolve(serviceVersion);
        Path rd = Paths.get(recipeDir);
        System.out.println("Artifacts in " + ad + "\nRecipes in " + rd);

        try {
            Files.createDirectories(ad);
            Files.createDirectories(rd);
            Path rp = rd.resolve(serviceName + "-" + serviceVersion + ".yaml");
            try (BufferedWriter out = Files.newBufferedWriter(rp)) {
                out.write(tls.toString());
            }
        } catch (IOException ex) {
            err("cli.tpl.err", ex);
            return false;
        }
        artifacts.forEach(fn -> {
            Path src = Paths.get(fn);
            Path dest = ad.resolve(src.getFileName());
            try {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                err("cli.tpl.cnc", fn);
                isOK = false;
            }
        });
        auxRecipies.forEach((name, body) -> {
            try (OutputStream out = Files.newOutputStream(rd.resolve(chopExtension(name) + ".yaml"), StandardOpenOption.CREATE)) {
                out.write(body);
            } catch (IOException ex) {
                err("cli.tpl.err", ex);
                isOK = false;
            }
        });
        return isOK;
    }

    private boolean harvestJar(Path pn) {
        try (JarFile jar = new JarFile(pn.toFile())) {
            Manifest m = jar.getManifest();
            if (m != null) {
                Attributes a = m.getMainAttributes();
                String s = a.getValue("ComponentVersion");
                if (!isEmpty(s)) {
                    serviceVersion = s;
                }
                s = a.getValue("ComponentName");
                if (!isEmpty(s)) {
                    serviceName = s;
                }
                s = a.getValue("Build-Jdk");
                Matcher jv = Pattern.compile("([0-9]+)\\..*").matcher(s);
                if (jv.matches()) {
                    javaVersion = jv.group(1);
                }

            }
            jar.stream().forEach(e -> {
                if (e.getName().startsWith("RECIPES/")) try {
                    addRecipe(e.getName(), capture(jar.getInputStream(e)));
                } catch (IOException ioe) {
                    err("cli.tpl.crj", e.getName());
                }
            });
        } catch (IOException ex) {
            err("cli.tpl.erd", pn);
            return false;
        }
        return true;
    }

    private void err(String tag) {
        err(tag, null);
    }

    private void err(String tag, Object aux) {
        String msg = ResourceBundle.getBundle("com.aws.greengrass.cli.CLI_messages")
                .getString(tag);
        if (aux != null) {
            msg = msg + ": " + aux;
        }
        throw new CommandLine.ParameterException(spec.commandLine(), msg);
    }

    private void addRecipe(String name, byte[] body) {
        int sl = name.lastIndexOf('/');
        if (sl >= 0) {
            name = name.substring(sl + 1);
        }
        if (name != null && body != null) {
            auxRecipies.put(name, body);
        }
    }

    private byte[] capture(Path in) {
        try (InputStream is = Files.newInputStream(in)) {
            return capture(is);
        } catch (IOException ex) {
            err("cli.tpl.erd", ex);
            return null;
        }
    }

    private byte[] capture(InputStream in) {
        if (in != null) {
            if (!(in instanceof BufferedInputStream)) {
                in = new BufferedInputStream(in);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int c;
            try {
                while ((c = in.read()) >= 0) {
                    out.write(c);
                }
            } catch (IOException ex) {
                err("cli.tpl.erd", ex);
            }
            close(in);
            return out.toByteArray();
        }

        return null;
    }

    @SuppressWarnings("UseSpecificCatch")
    private static void close(Closeable c) {
        if (c != null) try {
            c.close();
        } catch (Throwable t) {
        }
    }

    public class opHandlers {

        public String platform(String recipe) {
            try {
                InputStream in;
                URL u = getClass().getClassLoader().getResource("platforms/" + recipe);
                if (u != null) {
                    in = u.openStream();
                } else {
                    in = Files.newInputStream(localTemplateDir.resolve(recipe));
                }
                addRecipe(recipe, capture(in));
            } catch (IOException ex) {
                err("cli.tpl.erd", ex);
            }
            return "";
        }
    }

    public static String extension(String f) {
        if (f == null) {
            return "";
        }
        int dot = f.lastIndexOf('.');
        if (dot <= 0) {
            return "";
        }
        return f.substring(dot + 1);
    }

    public static String chopExtension(String f) {
        if (f == null) {
            return "";
        }
        int slash = f.lastIndexOf('/');
        if (slash >= 0) {
            f = f.substring(slash + 1);
        }
        int dot = f.lastIndexOf('.');
        if (dot <= 0) {
            return f;
        }
        return f.substring(0, dot);
    }

}
