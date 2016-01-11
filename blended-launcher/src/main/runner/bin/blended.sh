#!/bin/sh

function setenv() {

  if [ -f $BLENDED_HOME/bin/setenv ] ; then
    . $BLENDED_HOME/bin/setenv
  fi
}

function blended_home() {
  
  home=$1

  if [ x"$BLENDED_HOME" == "x" ] ; then
    OLDDIR=$(pwd)
    dir="$(dirname $0)/.."
    cd $dir
    home=$(pwd)
    cd $OLDDIR
  fi  

  echo "$home"
}

export BLENDED_HOME=$(blended_home $BLENDED_HOME)
setenv

cd $BLENDED_HOME

LAUNCHER_OPTS="--profile-lookup $BLENDED_HOME/launch.conf --init-profile-props"
JAVA_OPTS="-Dlogback.configurationFile=${BLENDED_HOME}/etc/logback.xml ${JAVA_OPTS}"
JAVA_OPTS="-Dlog4j.configurationFile=${BLENDED_HOME}/etc/log4j.xml -Dblended.home=${BLENDED_HOME} ${JAVA_OPTS}"
JAVA_OPTS="-Dsun.net.client.defaultConnectTimeout=500 -Dsun.net.client.defaultReadTimeout=500 ${JAVA_OPTS}"

RETVAL=2

if [ -n "$DEBUG_PORT" ] ; then
  JAVA_OPTS="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=${DEBUG_PORT},suspend=y ${JAVA_OPTS}"
fi

$JAVA_HOME/bin/java -version

while [ "x$RETVAL" == "x2" ]; do

${JAVA_HOME}/bin/java\
 $JAVA_OPTS\
 -cp\
 "${BLENDED_HOME}/etc:${BLENDED_HOME}/lib/*"\
 blended.launcher.Launcher\
 --framework-restart 0\
 ${LAUNCHER_OPTS}\
 "$@" && RETVAL=$? || RETVAL=$?

done

unset BLENDED_HOME
unset RETVAL