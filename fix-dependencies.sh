#!/bin/bash

set -x -e
rm -rf target/hola-swarm.jar.dir
unzip -q -d target/hola-swarm.jar.dir target/hola-swarm.jar
VERSION=`find target/hola-swarm.jar.dir -name 'httpclient*jar' | head -n 1 | sed 's|.*-\(.*\)\.jar$|\1|'`
echo "- org.apache.httpcomponents:httpclient:${VERSION}" >> target/hola-swarm.jar.dir/META-INF/wildfly-swarm-manifest.yaml
[ ! -f hola-swarm.jar.origin ] && mv target/hola-swarm.jar target/hola-swarm.jar.origin

cd target/hola-swarm.jar.dir
zip -q -r ../hola-swarm.jar .
cd -

