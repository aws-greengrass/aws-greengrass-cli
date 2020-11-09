## Automatic Recipe & Artifact generation

Normally, to deploy something to a device, you have to construct recipe and artifact directories and populate them.  This has some powerful uses, but it can be cumbersome for simple projects. Fortunately, they can be automatically generated for you from program code.  For example:
```
echo 'print("Hello from Python!")'>hello.py
greengrass-cli component update hello.py
```
Will generate recipe and artifact directories and deploy them to the device.  If you want to inspect (and possibly reuse) the generated files, they will be in `~/gg2Templates/hello`.

For a very simple case like this, the components name will be derived from the filename, in this case `hello`, and the version number will default to 0.0.0.  If the filename contains a version number, it will be taken from there.  For example, `hello-1.2.0` will have a version number of 1.2.0.

You can also embed component name and version information as comments in the source file itself.  For example, if `hello.lua` looked like this:
```
-- ComponentVersion: 1.1.0
-- ComponentName: OlaLua
print '¡Olá Lua!'
```
The components name and version would be OlaLua and 1.1.0.  The template for the app recipe will also cause an installation recipe for *lua* to be included.  After execution, gg2Templates will contain these files:
```
├── OlaLua
│   ├── artifacts
│   │   └── OlaLua
│   │       └── 1.1.0
│   │           └── hello.lua
│   └── recipes
│       ├── OlaLua-1.1.0.yaml
│       └── lua-5.3.0.yaml
```
And the generated recipe, `OlaLua-1.1.0.yaml` will look like this:
```
# 
---
RecipeFormatVersion: 2020-01-25
ComponentName: OlaLua
ComponentVersion: 1.1.0
ComponentDescription: Created for XXX on YYY from hello.lua
ComponentPublisher: XXX
ComponentDependencies:
  lua:
    VersionRequirement: ^5.1.0

Manifests:
  - Platform:
      os: linux
    Lifecycle:
      Run:
        cd {artifacts:path}; lua hello.lua
```

If the first two characters of the file are "`#!`", then it will be treated as an executable script:
```
#! /usr/bin/perl
use warnings;
print("Hello, World!\n");
```

`.jar` files are handled similarly, except that component name and version are searched for in the manifest, and the manifest must have a main class specification.  Also, if there is a RECIPIES folder in the jar, the contents will be copied to the recipe directory.
