#!/bin/sh
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out src/com/mycompany/routeph1/*.java && java -cp "out:lib/*" com.mycompany.routeph1.WebServer config.properties
