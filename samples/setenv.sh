#!/bin/bash
# Modify the following variables to conform to local install locations
if [ -z "$JAVA_CMD" ]; then
	JAVA_CMD=/usr/bin/java; export JAVA_CMD
fi
if [ -z "$JDBC_JARS" ]; then
	JDBC_JARS=/usr/local/lib/mysql-connector-java.jar
fi
if [ -z "$BENCH_HOME" ]; then
	BENCH_HOME=/Users/dhentchel/workspace/bench
fi

BENCH_CLASSPATH=${BENCH_HOME}/lib/bench.jar:${JDBC_JARS}

