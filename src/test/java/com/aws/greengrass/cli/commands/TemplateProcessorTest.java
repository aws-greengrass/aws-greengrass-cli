/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.CLI;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.*;
import java.nio.file.attribute.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;

public class TemplateProcessorTest {

    static Path temp;

    @BeforeAll
    public static void begin() {
        try {
            temp = Files.createTempDirectory("TPL");
        } catch (IOException ex) {
            fail("createTempDirectory: " + ex);
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
        test_single_file("hello-lua.lua", "print 'Hello from LUA!'");
    }

    @Test
    public void test_single_file_python() {
        test_single_file("hello-python.py", "print(\"Hello from Python!\")");
    }

    @Test
    public void test_single_file_hashbang() {
        test_single_file("hashbang", "#! /bin/sh\necho hello from sh");
    }

    @Test
    public void test_single_file_hashbangPerl() {
        test_single_file("helloPerl", "#!/usr/bin/perl\n"
                + "use warnings;\n"
                + "print(\"Hello, World!\\n\");");
    }

    public void test_single_file(String name, String body) {
        try {
            Path t = temp.resolve(name);
//            System.out.println("Temp file in " + t);
            try (Writer out = Files.newBufferedWriter(t, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                out.append(body).append('\n');
            }
            run("--ggcRootPath", System.getProperty("user.home")+"/.greengrass", "deployment", "create", t.toString());
        } catch (IOException ex) {
            fail(ex.toString());
        }
    }

    public int run(String... args) {
        return new CLI().runCommand(args);
    }

}
