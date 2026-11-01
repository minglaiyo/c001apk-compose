package app.awavenue.c001apk.compose.ui.captcha

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.awavenue.c001apk.compose.data.api.CaptchaChallenge
import app.awavenue.c001apk.compose.data.api.CoolapkHeaders
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-app Netease captcha page, ported from the Flutter app's
 * CaptchaPage: a WebView loads a local HTML page that initializes
 * the Netease (网易易盾) widget with the challenge's captchaId. The
 * widget type (click/slider) is decided server-side by Netease.
 *
 * On success the widget's validate value is handed back through the
 * CaptchaBridge JS interface; the caller formats it into the proof
 * and replays the gated request. The proof itself never enters
 * navigation arguments, logs, or an external browser URL.
 *
 * Android needs no local HTTP server for this page (unlike Flutter
 * on Windows): loadDataWithBaseURL supplies the base origin.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaWebView(
    challenge: CaptchaChallenge,
    onVerified: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Plain holder, not state: writing it from the AndroidView factory
    // must not trigger recomposition.
    val holder = remember { object { var webView: WebView? = null } }
    val finished = remember { AtomicBoolean(false) }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "人机验证",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { holder.webView?.reload() }) {
                Text("重新加载")
            }
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = CoolapkHeaders().userAgent()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                        ): Boolean {
                            val url = request.url.toString()
                            return !(url.startsWith("https://captcha.c001apk.invalid/") ||
                                url == "about:blank")
                        }
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onVerified(validate: String) {
                                if (!finished.compareAndSet(false, true)) return
                                Handler(Looper.getMainLooper()).post {
                                    onVerified(validate)
                                }
                            }
                        },
                        "CaptchaBridge",
                    )
                    loadDataWithBaseURL(
                        "https://captcha.c001apk.invalid/",
                        captchaHtml(challenge.captchaId),
                        "text/html",
                        "utf-8",
                        null,
                    )
                    holder.webView = this
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

/** Local captcha page, mirroring the Flutter app's embedded page. */
private fun captchaHtml(captchaId: String): String {
    return """<!doctype html><html lang="zh-CN"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>:root{color-scheme:light dark}body{font:16px sans-serif;margin:24px;color:CanvasText;background:Canvas}#captcha{margin-top:24px}p{line-height:1.6}</style>
</head><body><p id="status">正在加载验证组件…</p><div id="captcha"></div>
<script>
function statusText(text){document.getElementById('status').textContent=text;}
function startCaptcha(){
  var timeout=setTimeout(function(){statusText('验证组件加载超时，请点击右上角重新加载');},15000);
  var script=document.createElement('script');
  script.src='https://cstaticdun.126.net/load.min.js';
  script.onerror=function(){clearTimeout(timeout);statusText('验证组件加载失败，请检查网络后重新加载');};
  script.onload=function(){
    if(typeof initNECaptcha!=='function'){clearTimeout(timeout);statusText('验证组件不可用，请重新加载');return;}
    try { initNECaptcha({captchaId:'$captchaId',element:'#captcha',protocol:'https',
      mode:'embed',apiVersion:2,width:Math.min(320,window.innerWidth-48)+'px',lang:'zh-CN',
      onVerify:function(err,data){
        if(!err && data && data.validate){window.CaptchaBridge.onVerified(data.validate);}
        else{statusText('验证未通过，请按提示重试');}
      }
    },function(){clearTimeout(timeout);statusText('请按下方提示完成人机验证');},
      function(){clearTimeout(timeout);statusText('无法初始化验证，请检查网络后重新加载');});
    } catch(e){clearTimeout(timeout);statusText('验证组件初始化失败，请重新加载');}
  };
  document.head.appendChild(script);
}
startCaptcha();
</script></body></html>"""
}
