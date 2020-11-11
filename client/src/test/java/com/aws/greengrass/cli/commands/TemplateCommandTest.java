/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.CLI;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;

/**
 * This is a pretty simplistic test. It just checks that a selection of quick
 * template commands can dry-run without error. The generated files have to be
 * eyeballed, and deployment to an actual device has to be tested by hand. This
 * is wrong.
 */
public class TemplateCommandTest {

    static Path temp;

    @BeforeAll
    public static void begin() {
        try {
            temp = Files.createTempDirectory("TPL");
        } catch (IOException ex) {
            Assertions.fail("createTempDirectory: " + ex);
        }
    }

    @AfterAll
    public static void end() {
        try {
            Files.walkFileTree(temp, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ioe) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    try {
                        Files.deleteIfExists(dir);
                    } catch (IOException ioe) {
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            System.out.println("Couldn't delete: " + ex);
        }
    }

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
    }

    @Test
    public void test_single_file_jar() {
        String f = "/Users/jag/NetBeansProjects/HelloWorldForever/target/HelloWorldForever-1.0-SNAPSHOT.jar";
        if (new File(f).exists()) { // grotesque
            test_single_file(f);
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
        Assertions.assertTrue(run("--ggcRootPath",
                System.getProperty("user.home") + "/.greengrass",
                "quick", "--dryrun", name) == 0);
    }

    public int run(String... args) {
        return new CLI().runCommand(args);
    }

}
