#!/bin/sh
export CROWDIN_PERSONAL_TOKEN=`cat ~/.appconfig/eu.darken.capod/crowdin.key`
alias crowdin-cli='java -jar crowdin-cli.jar'
crowdin-cli "$@"