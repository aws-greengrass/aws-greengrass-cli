/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.impl.NucleusAdapterIpcClientImpl;
import picocli.CommandLine;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Closeable;
import java.io.File;
import java.io.Writer;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.RuntimeConstants;

@CommandLine.Command(name = "quick", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        description = "Quickly construct recipies and deploy a component based on provided files")
public class TemplateCommand extends BaseCommand {

    @CommandLine.Option(names = {"-dr", "--dryrun"},
            paramLabel = "Dry run: don't do any actual work")
    private boolean dryrun;

    @CommandLine.Option(names = "-gtd",
            paramLabel = "Generated Template Directory",
            defaultValue = "~/gg2Templates")
    private String generatedTemplateDirectory;

    @SuppressWarnings("FieldMayBeFinal")
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
    private RecipieFile keyFile;
    private final List<String> artifacts = new ArrayList<>();
    private String javaVersion = "11";
    private final Map<String, RecipieFile> recipes = new LinkedHashMap<>();

    @Override
    public void run() {
        genTemplateDir = Paths.get(NucleusAdapterIpcClientImpl.deTilde(generatedTemplateDirectory));
        if (files == null || files.length == 0) {
            err("cli.tpl.files");
        }
        scanFiles();
        if (keyFile == null) {
            err("cli.tpl.files");
        }
        build();
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
        args.add(keyFile.componentName + "=" + keyFile.componentVersion);
        if (!isEmpty(group)) {
            args.add("-g");
            args.add(group);
        }
        params.forEach(s -> {
            args.add("-p");
            args.add(s);
        });
        System.out.append(dryrun ? "DryRun" : "Executing").append(": greengrass-cli");
        args.forEach(s -> System.out.append(' ').append(s));
        System.out.println();
        if(!dryrun && parent.runCommand(args.toArray(new String[args.size()])) != 0) {
            err("cli.tpl.deploy");
        }
    }

    private int scanFiles() {
        for (String fn : files) {
            if (fn.indexOf('=') > 0) {
                // Assume that any argument with an = sign is a parameter.
                params.add(fn);
            } else {
                if (fn.endsWith(".jar")) {
                    if (keyFile == null) {
                        harvestJar(fn);
                    }
                    artifacts.add(fn);
                } else {
                    if (isRecipe(fn)) {
                        addRecipe(fn, capture(Paths.get(fn)));
                    } else {
                        addArtifact(fn, true);
                    }
                }
            }
        }
        return 0;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private void addArtifact(String fn, boolean xinfo) {
        if (keyFile == null && xinfo) {
            String body = null;
            try {
                body = ReadFirst(Paths.get(fn).toUri().toURL());
            } catch (MalformedURLException ex) {
                err("cli.tpl.erd", ex);
            }
            if (body != null) {
                keyFile = new RecipieFile(fn, body, false);
            }
        }
        artifacts.add(fn);
    }

    private String ReadFirst(URL u) {
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
            err("cli.tpl.erd", ioe);
        }
        return "";
    }

    @SuppressWarnings("UseSpecificCatch")
    public void build() {
        String componentName = keyFile.componentName;
        String componentVersion = keyFile.componentVersion;
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
        } else if (componentName != null) {
            artifactDir = genTemplateDir.resolve(componentName).resolve("artifacts").toString();
            recipeDir = genTemplateDir.resolve(componentName).resolve("recipes").toString();
        }
        generateTemplate();
        Path ad = Paths.get(artifactDir).resolve(componentName).resolve(componentVersion);
        Path rd = Paths.get(recipeDir);
        System.out.println("Artifacts in " + ad + "\nRecipes in " + rd);

        try {
            Files.createDirectories(ad);
            Files.createDirectories(rd);
        } catch (IOException ex) {
            err("cli.tpl.err", ex);
        }
        artifacts.forEach(fn -> {
            Path src = Paths.get(fn);
            Path dest = ad.resolve(src.getFileName());
            try {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                err("cli.tpl.cnc", fn);
            }
        });
        recipes.forEach((name, body) -> body.write(rd));
    }
    
    @SuppressWarnings("UseSpecificCatch")
    private void generateTemplate() {
        if (!keyFile.isRecipe) {
            keyPath = Paths.get(keyFile.filename);
            String templateName;
            if (keyFile.hashbang != null) {
                templateName = "hashbang.yml";
            } else if (Files.isExecutable(keyPath)) {
                templateName = "executable.yml";
            } else if (keyFile != null) {
                templateName = extension(keyFile.filename) + ".yml";
            } else {
                err("cli.tpl.nbasis", null);
                templateName = "error.yaml";
            }
            try {
                StringWriter tls = new StringWriter();
                getVelocityEngine()
                        .getTemplate("templates/" + templateName, "UTF-8")
                        .merge(context, tls);
                addRecipe(keyFile.componentName + '-' + keyFile.componentVersion, tls.toString());
            } catch (Throwable t) {
                err("cli.tpl.nft", keyFile);
            }
        } else {
            System.out.println("[ using provided recipe file ]"); //            ComponentRecipe r = getParsedRecipe();
        }
    }

    private VelocityEngine velocityEngine;
    private VelocityContext context;
    Path keyPath;

    private VelocityEngine getVelocityEngine() {
        if (velocityEngine == null) {
            velocityEngine = new VelocityEngine();
            context = new VelocityContext();
            velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "file,classpath");
            velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
            velocityEngine.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, localTemplateDir.toString());
            velocityEngine.init();
            context.put("name", keyFile.componentName);
            context.put("version", keyFile.componentVersion);
            context.put("publisher", !isEmpty(keyFile.componentPublisher)
                    ? keyFile.componentPublisher
                    : System.getProperty("user.name", "Unknown"));
            String description;
            if(keyFile!=null && !isEmpty(keyFile.componentDescription)) {
                description = keyFile.componentDescription;
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("Created for ")
                        .append(System.getProperty("user.name"))
                        .append(" on ")
                        .append(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                        .append(" from");
                for (String f : files) {
                    sb.append(' ').append(f);
                }
                description = sb.toString();
            }
            context.put("description", description);
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
            if (keyFile.hashbang != null) {
                context.put("hashbang", keyFile.hashbang);
            }
            if (javaVersion != null) {
                context.put("javaVersion", javaVersion);
            }
        }
        return velocityEngine;
    }

    private void harvestJar(String pn) {
        try (JarFile jar = new JarFile(new File(pn))) {
            Manifest m = jar.getManifest();
            if (m != null) {
                Attributes a = m.getMainAttributes();
                StringBuilder body = new StringBuilder();
                String s = a.getValue("ComponentVersion");
                if (!isEmpty(s)) {
                    body.append("ComponentVersion: ").append(s).append("\n");
                }
                s = a.getValue("ComponentName");
                if (!isEmpty(s)) {
                    body.append("ComponentName: ").append(s).append("\n");
                }
                s = a.getValue("Build-Jdk");
                Matcher jv = Pattern.compile("([0-9]+)\\..*").matcher(s);
                if (jv.matches()) {
                    javaVersion = jv.group(1);
                }
                keyFile = new RecipieFile(pn, body.toString(), false);
            }
            jar.stream().forEach(e -> {
                String name = e.getName();
                if (name.startsWith("RECIPES/") && isRecipe(name)) {
                    try (Reader in = new InputStreamReader(jar.getInputStream(e))) {
                        addRecipe(e.getName(), capture(in));
                    } catch (IOException ioe) {
                        err("cli.tpl.crj", e.getName());
                    }
                }
            });
        } catch (IOException ex) {
            err("cli.tpl.erd", pn);
        }
    }

    private static final Pattern RECIPEPATTERN = Pattern.compile(".*\\.(yaml|yml|ggr)$");

    private static boolean isRecipe(String name) {
        return RECIPEPATTERN.matcher(name).matches();
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

    private RecipieFile addRecipe(String name, String body) {
        RecipieFile ret = null;
        if (name != null && body != null) {
            int sl = name.lastIndexOf('/');
            if (sl >= 0) {
                name = name.substring(sl + 1);
            }
            ret = new RecipieFile(name, body, true);
            if (keyFile == null) {
                keyFile = ret;
            }
            recipes.put(ret.componentName, ret);
        }
        return ret;
    }

    private String capture(Path in) {
        try (Reader is = Files.newBufferedReader(in)) {
            return capture(is);
        } catch (IOException ex) {
            err("cli.tpl.erd", ex);
            return null;
        }
    }

    private String capture(Reader in) {
        if (in != null) {
            StringWriter out = new StringWriter();
            int c;
            try {
                while ((c = in.read()) >= 0) {
                    out.write(c);
                }
            } catch (IOException ex) {
                err("cli.tpl.erd", ex);
            }
            close(in);
            return out.toString();
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

    @SuppressWarnings("UseSpecificCatch")
    public class opHandlers {

        public String platform(String recipe) {
            try {
                StringWriter out = new StringWriter();
                getVelocityEngine()
                        .getTemplate("platforms/" + recipe, "UTF-8")
                        .merge(context, out);
                addRecipe(recipe, out.toString());
            } catch (Throwable ex) {
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
        Matcher m = Pattern.compile("\\.[a-zA-Z][^.-]*$").matcher(f);
        if(m.find())
            f = f.substring(0,m.start());
        return f;
    }
    
    public static String cleanVersion(String version) {
        if(isEmpty(version)) version = "0.0.0";
        if (version.endsWith("-SNAPSHOT")) {
            version = version.substring(0,version.length() - 9);
        }
        return version;
    }

    class RecipieFile {

        private final String body;
        final String componentName;
        final String componentVersion;
        final String componentDescription;
        final String componentPublisher;
        final String group;
        final String hashbang;
        final String filename;
        final boolean isRecipe;

        RecipieFile(String fn, String b, boolean is) {
            isRecipe = is;
            filename = fn;
            String name = chopExtension(fn);
            Matcher version = Pattern.compile("-[0-9]").matcher(name);
            String p1, p2;
            if (version.find()) {
                p1 = name.substring(0, version.start());
                p2 = name.substring(version.start() + 1);
            } else {
                p1 = name;
                p2 = "0.0.0";
            }
            body = b;
            componentName = getPart("name", p1);
            componentVersion = cleanVersion(getPart("version", p2));
            componentDescription = getPart("description", null);
            componentPublisher = getPart("publisher", null);
            group = getPart("Group", null);
            Matcher m = Pattern.compile("#! *(.*)").matcher(body);
            hashbang = m.lookingAt() ? m.group(1) : null;
        }

        private String getPart(String part, String dflt) {
            Matcher m = Pattern.compile("component[ -_]?" + part + ": *([^;,\n\"]+)", Pattern.CASE_INSENSITIVE).matcher(body);
            return clean(m.find() ? m.group(1) : dflt, dflt);
        }

        private String clean(String s, String dflt) {
            if (s == null) {
                return dflt;
            }
            Matcher m = Pattern.compile(" *([^ #]+)").matcher(s);
            if (m.lookingAt()) {
                s = m.group(1);
            }
            s = s.trim();
            return isEmpty(s) ? dflt : s;
        }

        public void write(Path dir) {
            if (isRecipe) {
                Path fn = dir.resolve(componentName + '-' + componentVersion + ".yaml");
                System.out.println("Writing " + fn);
                try (Writer out = Files.newBufferedWriter(fn,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    out.write(body);
                } catch (IOException ex) {
                    err("cli.tpl.err", ex);
                }
            }
        }
    }
}
