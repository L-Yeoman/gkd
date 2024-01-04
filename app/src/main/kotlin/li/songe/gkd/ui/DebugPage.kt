package li.songe.gkd.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.MainActivity
import li.songe.gkd.appScope
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.shizuku.newActivityTaskManager
import li.songe.gkd.shizuku.safeGetTasks
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.SnapshotPageDestination
import li.songe.gkd.util.Ext
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.authActionFlow
import li.songe.gkd.util.canDrawOverlaysAuthAction
import li.songe.gkd.util.checkOrRequestNotifPermission
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateStorage
import li.songe.gkd.util.usePollState
import rikka.shizuku.Shizuku

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun DebugPage() {
    val context = LocalContext.current as MainActivity
    val launcher = LocalLauncher.current
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()

    var showPortDlg by remember {
        mutableStateOf(false)
    }

    Scaffold(topBar = {
        TopAppBar(navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = { Text(text = "高级模式") }, actions = {})
    }, content = { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(0.dp, 10.dp)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val shizukuIsOk by usePollState { shizukuIsSafeOK() }
            if (!shizukuIsOk) {
                AuthCard(title = "Shizuku授权",
                    desc = "高级运行模式,能更准确识别界面活动ID",
                    onAuthClick = {
                        try {
                            Shizuku.requestPermission(Activity.RESULT_OK)
                        } catch (e: Exception) {
                            LogUtils.d("Shizuku授权错误", e)
                            ToastUtils.showShort("Shizuku可能没有运行")
                        }
                    })
                Divider()
            } else {
                TextSwitch(name = "Shizuku模式",
                    desc = "高级运行模式,能更准确识别界面活动ID",
                    checked = store.enableShizuku,
                    onCheckedChange = { enableShizuku ->
                        if (enableShizuku) {
                            appScope.launchTry(Dispatchers.IO) {
                                // 校验方法是否适配, 再允许使用 shizuku
                                val tasks = newActivityTaskManager()?.safeGetTasks()?.firstOrNull()
                                if (tasks != null) {
                                    updateStorage(
                                        storeFlow, store.copy(
                                            enableShizuku = true
                                        )
                                    )
                                } else {
                                    ToastUtils.showShort("Shizuku方法校验失败,无法使用")
                                }
                            }
                        } else {
                            updateStorage(
                                storeFlow, store.copy(
                                    enableShizuku = false
                                )
                            )
                        }

                    })
                Divider()
            }

            val httpServerRunning by HttpService.isRunning.collectAsState()
            TextSwitch(
                name = "HTTP服务",
                desc = if (httpServerRunning) "浏览器打开下面任意链接即可自动连接\n${
                    Ext.getIpAddressInLocalNetwork()
                        .map { host -> "http://${host}:${store.httpServerPort}" }.joinToString("\n")
                }" else "开启HTTP服务在同一局域网下连接调试工具",
                checked = httpServerRunning
            ) {
                if (!checkOrRequestNotifPermission(context)) {
                    return@TextSwitch
                }
                if (it) {
                    HttpService.start()
                } else {
                    HttpService.stop()
                }
            }
            Divider()

            SettingItem(
                title = "HTTP服务端口-${store.httpServerPort}", imageVector = Icons.Default.Edit
            ) {
                showPortDlg = true
            }
            Divider()

            TextSwitch(
                name = "自动清除内存订阅",
                desc = "当HTTP服务关闭时,清除内存订阅",
                checked = store.autoClearMemorySubs
            ) {
                updateStorage(
                    storeFlow, store.copy(
                        autoClearMemorySubs = it
                    )
                )
            }
            Divider()

            SettingItem(title = "快照记录", onClick = {
                navController.navigate(SnapshotPageDestination)
            })
            Divider()

            val screenshotRunning by ScreenshotService.isRunning.collectAsState()
            TextSwitch(
                name = "截屏服务",
                desc = "生成快照需要获取屏幕截图,Android11无需开启",
                checked = screenshotRunning,
                onCheckedChange = appScope.launchAsFn<Boolean> {
                    if (!checkOrRequestNotifPermission(context)) {
                        return@launchAsFn
                    }
                    if (it) {
                        val mediaProjectionManager =
                            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        val activityResult =
                            launcher.launchForResult(mediaProjectionManager.createScreenCaptureIntent())
                        if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
                            ScreenshotService.start(intent = activityResult.data!!)
                        }
                    } else {
                        ScreenshotService.stop()
                    }
                })
            Divider()

            val floatingRunning by FloatingService.isRunning.collectAsState()
            TextSwitch(
                name = "悬浮窗服务",
                desc = "显示截屏按钮,便于用户主动保存快照",
                checked = floatingRunning
            ) {
                if (!checkOrRequestNotifPermission(context)) {
                    return@TextSwitch
                }
                if (it) {
                    if (Settings.canDrawOverlays(context)) {
                        val intent = Intent(context, FloatingService::class.java)
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        authActionFlow.value = canDrawOverlaysAuthAction
                    }
                } else {
                    FloatingService.stop(context)
                }
            }
            Divider()
            TextSwitch(
                name = "音量快照",
                desc = "当音量变化时,生成快照,如果悬浮窗按钮不工作,可以使用这个",
                checked = store.captureVolumeChange
            ) {
                updateStorage(
                    storeFlow, store.copy(
                        captureVolumeChange = it
                    )
                )
            }

            Divider()
            TextSwitch(
                name = "截屏快照",
                desc = "当用户截屏时保存快照(需手动替换快照图片),仅支持部分MIUI14",
                checked = store.captureScreenshot
            ) {
                updateStorage(
                    storeFlow, store.copy(
                        captureScreenshot = it
                    )
                )
            }

            Divider()
            TextSwitch(
                name = "隐藏快照状态栏",
                desc = "当保存快照时,隐藏截图里的顶部状态栏高度区域",
                checked = store.hideSnapshotStatusBar
            ) {
                updateStorage(
                    storeFlow, store.copy(
                        hideSnapshotStatusBar = it
                    )
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    })

    if (showPortDlg) {
        Dialog(onDismissRequest = { showPortDlg = false }) {
            var value by remember {
                mutableStateOf(store.httpServerPort.toString())
            }
            AlertDialog(title = { Text(text = "请输入新端口") }, text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it.filter { c -> c.isDigit() }.take(5)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = "${value.length} / 5",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                )
            }, onDismissRequest = { showPortDlg = false }, confirmButton = {
                TextButton(onClick = {
                    val newPort = value.toIntOrNull()
                    if (newPort == null || !(5000 <= newPort && newPort <= 65535)) {
                        ToastUtils.showShort("请输入在 5000~65535 的任意数字")
                        return@TextButton
                    }
                    updateStorage(
                        storeFlow, store.copy(
                            httpServerPort = newPort
                        )
                    )
                    showPortDlg = false
                }) {
                    Text(
                        text = "确认", modifier = Modifier
                    )
                }
            }, dismissButton = {
                TextButton(onClick = { showPortDlg = false }) {
                    Text(
                        text = "取消"
                    )
                }
            })
        }
    }
}