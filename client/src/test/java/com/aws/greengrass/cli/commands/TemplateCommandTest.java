/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.CLI;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

/**
 * This is a pretty simplistic test. It just checks that a selection of quick
 * template commands can dry-run without error. The generated files have to be
 * eyeballed, and deployment to an actual device has to be tested by hand. This
 * is wrong.
 */
public class TemplateCommandTest {

    @TempDir
    static Path temp;
//    static {
//        temp = Paths.get(System.getProperty("user.home", "/tmp")).resolve("gg2Templates");
//        try {
//            Files.createDirectories(temp);
//        } catch (IOException ex) {
//            Assertions.fail(ex);
//        }
//    }

    @Test
    public void test_single_file_lua() {
        test_single_file("hello.lua", "-- ComponentVersion: 1.1.0\n"
                + "-- ComponentName: OlaLua\n"
                + "print '¡Olá lua!'");
    }

    @Test
    public void test_single_file_python() {
        test_single_file("hello.py", "print(\"Hello from Python!\")");
    }

    @Test
    public void test_single_file_hashbang() {
        test_single_file("hashbang", "#! /bin/sh\n"
                + "# ComponentVersion: 1.2.3\n"
                + "# ComponentName: ShellTest\n"
                + "echo hello from sh");
    }

    @Test
    public void test_single_file_hashbangPerl() {
        test_single_file("helloPerl", "#!/usr/bin/perl\n"
                + "use warnings;\n"
                + "print(\"Hello, World!\\n\");");
        expect("helloPerl/recipes/helloPerl-0.0.0.yaml");
        expect("helloPerl/artifacts/helloPerl/0.0.0/helloPerl");
    }

    @Test
    public void test_single_file_jar() {
        String f = "/Users/jag/NetBeansProjects/HelloWorldForever/target/HelloWorldForever-1.0-SNAPSHOT.jar";
        if (new File(f).exists()) { // grotesque
            test_single_file(f);
            expect("HelloWorldForever/recipes/java-11.0.0.yaml");
            expect("HelloWorldForever/recipes/useless-0.0.0.yaml");
            expect("HelloWorldForever/recipes/HelloWorldForever-1.0.yaml");
        }
    }

    @Test
    public void test_failure() {
        Assertions.assertTrue(run("--ggcRootPath",
                System.getProperty("user.home") + "/.greengrass",
                "quick", "--dryrun", "foo.bar") != 0);
    }

    @Test
    public void test_failure2() {
        Assertions.assertTrue(run("--ggcRootPath",
                System.getProperty("user.home") + "/.greengrass",
                "quick", "--dryrun", "foo.py") != 0);
    }

    public void test_single_file(String name, String body) {
        try {
            Path t = temp.resolve(name);
            try (Writer out = Files.newBufferedWriter(t, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                out.append(body).append('\n');
            }
            test_single_file(t.toString());
        } catch (IOException ex) {
            Assertions.fail(ex.toString());
        }
    }

    public void test_single_file(String name) {
        try {
            System.out.println("Testing " + name);
            Assertions.assertTrue(run("--ggcRootPath",
                    "/opt/GGv2",
                    "quick", "--dryrun",
                    "-gtd", templates.toString(),
                    name) == 0);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            Assertions.fail(t.toString());
        }
    }

    public void expect(String path) {
        Path p = templates.resolve(path);
        System.out.println("Expecting: " + p);
        Assertions.assertTrue(Files.exists(p), p.toString());
    }

    public int run(String... args) {
        return new CLI().runCommand(args);
    }

    static Path templates;

    @BeforeAll
    static public void setup() {
        templates = temp.resolve("templates");
    }

    @AfterAll
    static public void displayTree() {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "tree", ".");
            pb.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
            pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
            pb.directory(templates.toFile());
            Process p = pb.start();
            try (InputStream in = p.getInputStream()) {
                int c;
                while ((c = in.read()) > 0) {
                    System.out.write(c);
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException ex) {
            Assertions.fail(ex);
        }
    }

}
