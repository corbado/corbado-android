package com.corbado.connect.example

enum class CorbadoEndpoint(val path: String) {
    LoginInit("/v2/connect/login/init"),
    LoginStart("/v2/connect/login/start"),
    LoginFinish("/v2/connect/login/finish"),
    AppendInit("/v2/connect/append/init"),
    AppendStart("/v2/connect/append/start"),
    AppendFinish("/v2/connect/append/finish"),
    ManageInit("/v2/connect/manage/init"),
    ManageList("/v2/connect/manage/list"),
    ManageDelete("/v2/connect/manage/delete"),
}
