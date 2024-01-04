package li.songe.gkd.debug

import android.content.Context
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.composition.CompositionService
import li.songe.gkd.data.DeviceInfo
import li.songe.gkd.data.GkdAction
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.debug.SnapshotExt.captureSnapshot
import li.songe.gkd.notif.createNotif
import li.songe.gkd.notif.httpChannel
import li.songe.gkd.notif.httpNotif
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.util.Ext.getIpAddressInLocalNetwork
import li.songe.gkd.util.SERVER_SCRIPT_URL
import li.songe.gkd.util.keepNullJson
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.updateSubscription
import java.io.File


class HttpService : CompositionService({
    val context = this
    val scope = CoroutineScope(Dispatchers.IO)

    val httpSubsItem = SubsItem(
        id = -1L,
        order = -1,
        enableUpdate = false,
    )

    val httpSubsRawFlow = MutableStateFlow<RawSubscription?>(null)
    fun createServer(port: Int): CIOApplicationEngine {
        return embeddedServer(CIO, port) {
            install(KtorCorsPlugin)
            install(KtorErrorPlugin)
            install(ContentNegotiation) { json(keepNullJson) }

            routing {
                get("/") { call.respondText(ContentType.Text.Html) { "<script type='module' src='$SERVER_SCRIPT_URL'></script>" } }
                route("/api") {
                    get("/device") { call.respond(DeviceInfo.instance) }
                    get("/snapshot") {
                        val id = call.request.queryParameters["id"]?.toLongOrNull()
                            ?: throw RpcError("miss id")
                        val fp = File(SnapshotExt.getSnapshotPath(id))
                        if (!fp.exists()) {
                            throw RpcError("对应快照不存在")
                        }
                        call.respondFile(fp)
                    }
                    get("/screenshot") {
                        val id = call.request.queryParameters["id"]?.toLongOrNull()
                            ?: throw RpcError("miss id")
                        val fp = File(SnapshotExt.getScreenshotPath(id))
                        if (!fp.exists()) {
                            throw RpcError("对应截图不存在")
                        }
                        call.respondFile(fp)
                    }
                    get("/captureSnapshot") {
                        call.respond(captureSnapshot())
                    }
                    get("/snapshots") {
                        call.respond(DbSet.snapshotDao.query().first())
                    }
                    get("/subsApps") {
                        call.respond(httpSubsRawFlow.value?.apps ?: emptyList())
                    }
                    post("/updateSubsApps") {

                        val subsStr =
                            """{"name":"内存订阅","id":-1,"version":0,"author":"@gkd-kit/inspect","apps":${call.receiveText()}}"""
                        try {
                            val httpSubsRaw = RawSubscription.parse(subsStr)
                            updateSubscription(httpSubsRaw)
                            DbSet.subsItemDao.insert((subsItemsFlow.value.find { s -> s.id == httpSubsItem.id }
                                ?: httpSubsItem).copy(mtime = System.currentTimeMillis()))
                        } catch (e: Exception) {
                            throw RpcError(e.message ?: "未知")
                        }
                        call.respond(RpcOk())
                    }
                    post("/execSelector") {
                        if (!GkdAbService.isRunning.value) {
                            throw RpcError("无障碍没有运行")
                        }
                        val gkdAction = call.receive<GkdAction>()
                        LogUtils.d(gkdAction)
                        call.respond(GkdAbService.execAction(gkdAction))
                    }
                }
            }
        }
    }

    var server: CIOApplicationEngine? = null
    scope.launchTry(Dispatchers.IO) {
        storeFlow.map(scope) { s -> s.httpServerPort }.collect { port ->
            server?.stop()
            server = try {
                createServer(port).apply { start() }
            } catch (e: Exception) {
                LogUtils.d("HTTP服务启动失败", e)
                null
            }
            if (server == null) {
                ToastUtils.showShort("HTTP服务启动失败,您可以尝试切换端口后重新启动")
                stopSelf()
                return@collect
            }
            createNotif(
                context, httpChannel.id, httpNotif.copy(text = "HTTP服务正在运行-端口$port")
            )
            LogUtils.d(*getIpAddressInLocalNetwork().map { host -> "http://${host}:${port}" }
                .toList().toTypedArray())
        }
    }


    onDestroy {
        httpSubsRawFlow.value = null
        scope.launchTry(Dispatchers.IO) {
            server?.stop()
            if (storeFlow.value.autoClearMemorySubs) {
                httpSubsItem.removeAssets()
            }
            delay(3000)
            scope.cancel()
        }
    }

    isRunning.value = true
    onDestroy {
        isRunning.value = false
    }
}) {
    companion object {
        val isRunning = MutableStateFlow(false)
        fun stop(context: Context = app) {
            context.stopService(Intent(context, HttpService::class.java))
        }

        fun start(context: Context = app) {
            context.startForegroundService(Intent(context, HttpService::class.java))
        }

    }
}

@Serializable
data class RpcOk(
    val message: String? = null,
)

fun clearHttpSubs() {
    // 如果 app 被直接在任务列表划掉, HTTP订阅会没有清除, 所以在后续的第一次启动时清除
    if (HttpService.isRunning.value) return
    appScope.launchTry(Dispatchers.IO) {
        delay(1000)
        if (storeFlow.value.autoClearMemorySubs) {
            SubsItem(
                id = -1L,
                order = -1,
                enableUpdate = false,
            ).removeAssets()
        }
    }
}