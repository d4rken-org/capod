export CROWDIN_API_KEY=`cat ~/.appconfig/eu.darken.capod/crowdin.key`
alias crowdin-cli='java -jar crowdin-cli.jar'
crowdin-cli "$@"
