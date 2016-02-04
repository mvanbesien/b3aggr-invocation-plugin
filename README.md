# b3aggr-invocation-plugin
Maven Plugin that triggers Eclipse's B3 Aggregation, to build unified Update Site from unitary ones.

To use this plugin using the command line, anywhere from your filesystem, you can run the mojo as following:

mvn fr.mvanbesien.mojos:b3aggr-invocation-plugin:LATEST:aggregate -Dbuild-model=<PATH-TO-YOUR-b3aggr-FILE>

If you need to use a proxy, just set the http.proxyHost/http.proxyPort values in the command line. The translation to Eclipse properties is handled by the plugin.

Build status : ![](https://travis-ci.org/mvanbesien/b3aggr-invocation-plugin.svg?branch=master)
