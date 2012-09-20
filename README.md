ChConstructor
=============
Building:
---------
make sure you have 'maven' installed then execute

    mvn compile assembly:single

alternatively we have provided a single command Unix script for this

    ./build.sh

and you can find the excutable jar with packaged dependencies in the
target directory.

Running:
--------
To see how to run ChConstructor compile it and execute

    java -jar chconstructorg.jar --help

IDE Integration:
----------------
Because we use maven this project can easily be imported into several IDEs
including IntelliJ and Eclipse, just look for 'maven import'
