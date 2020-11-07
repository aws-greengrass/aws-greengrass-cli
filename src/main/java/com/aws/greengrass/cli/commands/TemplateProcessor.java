/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.jar.*;
import java.util.logging.*;
import java.util.regex.*;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

public class TemplateProcessor {

    private String recipeDir;
    private String artifactDir;
    private Path genTemplateDir;
    private Map<String, String> whatToMerge;
    private Path localTemplateDir;
    private String keyFile = null;
    private String serviceName = null;
    private String serviceVersion = null;
    private boolean generateRecipe = true;
    private final List<String> artifacts = new ArrayList<>();
    private final String[] files;
    private String hashbang;
    private String javaVersion = "11";
    private Map<String, byte[]> auxRecipies = new LinkedHashMap<>();

    public TemplateProcessor(String[] files) {
        this.files = files;
        for (String fn : files) {
            Path pn = Paths.get(fn);
            switch (extension(fn)) {
                default:
                    addArtifact(fn, true);
                    break;
                case "jar":
                    if (serviceName == null) {
                        harvestJar(pn);
                    }
                    addArtifact(fn, false);
                    break;
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
                Logger.getLogger(TemplateProcessor.class.getName()).log(Level.SEVERE, null, ex);
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
            m = Pattern.compile("ComponentName: *([^;,\n\"]*)", Pattern.CASE_INSENSITIVE).matcher(body);
            if (m.find()) {
                serviceName = m.group(1);
            }
            m = Pattern.compile("ComponentVersion: *([^;,\n\"]*)", Pattern.CASE_INSENSITIVE).matcher(body);
            if (m.find()) {
                serviceVersion = m.group(1);
            }
        }
    }

    private String readAll(URL u) {
        if (u != null) {
            try (Reader in = new InputStreamReader(new BufferedInputStream(u.openStream()))) {
                StringBuilder sb = new StringBuilder();
                int c;
                int limit = 4000;
                while ((c = in.read()) >= 0 && --limit >= 0) {
                    sb.append((char) c);
                }
                return sb.toString();
            } catch (IOException ioe) {
            }
        }
        return null;
    }

    public void build() {
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
            ve.setProperty("resource.loader", "file,classpath");
            ve.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
            ve.setProperty("resource.loader.file.path", localTemplateDir.toString());

            ve.init();
            String templateName;
            Path keyPath = Paths.get(keyFile);
            if (isHashBang(keyFile)) {
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
            Template t = ve.getTemplate("templates/" + templateName, "UTF-8");
            t.merge(context, tls);
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
            System.err.println("ERROR: " + ex);
            System.exit(-1);
        }
        artifacts.forEach(fn -> {
            Path src = Paths.get(fn);
            Path dest = ad.resolve(src.getFileName());
            try {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                System.err.println("Could not copy artifact " + fn + "\n\t" + ex);
            }
        });
        auxRecipies.forEach((name, body) -> {
            try (OutputStream out = Files.newOutputStream(rd.resolve(chopExtension(name) + ".yaml"), StandardOpenOption.CREATE)) {
                out.write(body);
            } catch (IOException ex) {
                Logger.getLogger(TemplateProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        addMerge(serviceName, serviceVersion);
    }

    private void addMerge(String name, String version) {
        if (whatToMerge == null) {
            whatToMerge = new HashMap<>();
        }
        whatToMerge.put(name, version);
    }

    private boolean isHashBang(String keyFile) {
        try (BufferedReader in = Files.newBufferedReader(Paths.get(keyFile))) {
            if (in.read() != '#' || in.read() != '!') {
                return false;
            }
            hashbang = in.readLine().trim();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private void harvestJar(Path pn) {
        try {
            JarFile jar = new JarFile(pn.toFile());
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
                    System.err.println("Can't read .jar component " + e.getName());
                    System.exit(-1);
                }
            });
        } catch (IOException ex) {
            System.err.println("Error reading " + pn + "\n\t" + ex);
        }
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

    private static byte[] capture(Path in) {
        try (InputStream is = Files.newInputStream(in)) {
            return capture(is);
        } catch (IOException ex) {
            System.err.println("Can't open " + in + ", ignoring\n\t" + ex);
            return null;
        }
    }

    private static byte[] capture(InputStream in) {
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
                System.err.println("Can't read file, ignoring\n\t" + ex);
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
                System.err.println("Could not copy platform file " + recipe + "\n\t" + ex);
                return " [Error: " + ex.toString() + "] ";
            }
            return "";
        }
    }

//<editor-fold defaultstate="collapsed" desc="boilerplate">
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

    /**
     * @return the recipeDir
     */
    public String getRecipeDir() {
        return recipeDir;
    }

    /**
     * @param recipeDir the recipeDir to set
     */
    public void setRecipeDir(String recipeDir) {
        this.recipeDir = recipeDir;
    }

    /**
     * @return the artifactDir
     */
    public String getArtifactDir() {
        return artifactDir;
    }

    /**
     * @param artifactDir the artifactDir to set
     */
    public void setArtifactDir(String artifactDir) {
        this.artifactDir = artifactDir;
    }

    /**
     * @return the genTemplateDir
     */
    public String getGenTemplateDir() {
        return genTemplateDir.toString();
    }

    /**
     * @param genTemplateDir the genTemplateDir to set
     */
    public void setGenTemplateDir(String genTemplateDir) {
        this.genTemplateDir = Paths.get(genTemplateDir);
    }

    public Map<String, String> getWhatToMerge() {
        return whatToMerge;
    }

    public void setWhatToMerge(Map<String, String> w) {
        whatToMerge = w;
    }
//</editor-fold>
}
