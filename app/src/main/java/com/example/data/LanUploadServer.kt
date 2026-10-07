package com.example.data

import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class LanUploadServer(
    private val port: Int = 8088,
    private val onContentReceived: (String) -> Unit
) {
    companion object {
        private const val TAG = "LanUploadServer"
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(): String? {
        if (isRunning) return getLocalIpAddress()
        return try {
            serverSocket = ServerSocket(port)
            isRunning = true
            scope.launch {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
            getLocalIpAddress()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LAN server on port $port", e)
            null
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        scope.cancel()
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address && addr.isSiteLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getServerUrl(): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$port"
    }

    private fun handleClient(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            var contentLength = 0
            var contentType = ""
            var headerLine: String?

            while (reader.readLine().also { headerLine = it } != null) {
                if (headerLine.isNullOrBlank()) break
                val lower = headerLine!!.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = lower.substring(15).trim().toIntOrNull() ?: 0
                }
                if (lower.startsWith("content-type:")) {
                    contentType = headerLine!!.substring(13).trim()
                }
            }

            if (method.equals("POST", ignoreCase = true)) {
                // Read body
                val bodyChars = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(bodyChars, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                val body = String(bodyChars, 0, totalRead)

                val m3uContent = extractM3uFromBody(body, contentType)
                if (m3uContent.isNotBlank()) {
                    onContentReceived(m3uContent)
                    sendResponse(output, 200, "text/html; charset=utf-8", getSuccessHtml())
                } else {
                    sendResponse(output, 400, "text/html; charset=utf-8", "<h3>上传的内容为空或无效</h3>")
                }
            } else {
                // GET request -> serve web form
                sendResponse(output, 200, "text/html; charset=utf-8", getUploadFormHtml())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun extractM3uFromBody(body: String, contentType: String): String {
        return if (contentType.contains("multipart/form-data")) {
            // Find boundary and extract file or text payload
            val lines = body.lines()
            val contentLines = mutableListOf<String>()
            var isRecording = false
            for (line in lines) {
                if (line.startsWith("------")) {
                    isRecording = false
                } else if (isRecording) {
                    contentLines.add(line)
                } else if (line.isEmpty() && !isRecording) {
                    isRecording = true
                }
            }
            contentLines.joinToString("\n").trim()
        } else if (body.startsWith("content=")) {
            java.net.URLDecoder.decode(body.substring(8), "UTF-8")
        } else {
            body.trim()
        }
    }

    private fun sendResponse(output: OutputStream, code: Int, contentType: String, body: String) {
        val writer = PrintWriter(OutputStreamWriter(output, Charsets.UTF_8))
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $code OK\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${bodyBytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        output.write(bodyBytes)
        output.flush()
    }

    private fun getUploadFormHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>DIYP 电视直播 - 局域网传源</title>
                <style>
                    body { font-family: -apple-system, sans-serif; background: #0f141c; color: #e2e8f0; margin: 0; padding: 20px; }
                    .card { max-width: 600px; margin: 20px auto; background: #1a2230; padding: 24px; border-radius: 12px; border: 1px solid #2d3748; }
                    h2 { color: #00e5ff; margin-top: 0; }
                    p { color: #94a3b8; font-size: 14px; }
                    textarea { width: 100%; height: 200px; background: #0b0f17; color: #fff; border: 1px solid #334155; border-radius: 8px; padding: 12px; font-family: monospace; box-sizing: border-box; }
                    input[type="file"] { margin: 12px 0; color: #94a3b8; }
                    button { background: #0091ea; color: #fff; border: none; padding: 12px 24px; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; width: 100%; margin-top: 12px; }
                    button:hover { background: #00b0ff; }
                    .badge { background: #0284c7; color: #fff; padding: 4px 8px; border-radius: 4px; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>📺 DIYP 电视直播 · 局域网快速传源</h2>
                    <p>在手机或电脑浏览器中上传或粘贴 M3U 播放列表，直接推送到电视！</p>
                    
                    <h3>方法一：选择电脑/手机上的 M3U 文件</h3>
                    <input type="file" id="fileInput" accept=".m3u,.m3u8,.txt" />
                    
                    <h3>方法二：直接粘贴 M3U / DIYP 文本</h3>
                    <textarea id="m3uText" placeholder="#EXTM3U&#10;#EXTINF:-1,CCTV-1 综合&#10;http://...&#10;&#10;或者 DIYP 格式:&#10;央视频道,#genre#&#10;CCTV-1,http://..."></textarea>
                    
                    <button onclick="uploadContent()">🚀 立即推送到电视播放</button>
                </div>

                <script>
                    const fileInput = document.getElementById('fileInput');
                    const textArea = document.getElementById('m3uText');

                    fileInput.addEventListener('change', function(e) {
                        const file = e.target.files[0];
                        if (!file) return;
                        const reader = new FileReader();
                        reader.onload = function(evt) {
                            textArea.value = evt.target.result;
                        };
                        reader.readAsText(file);
                    });

                    function uploadContent() {
                        const content = textArea.value.trim();
                        if (!content) {
                            alert('请先选择文件或粘贴 M3U 内容！');
                            return;
                        }
                        fetch('/', {
                            method: 'POST',
                            headers: { 'Content-Type': 'text/plain; charset=utf-8' },
                            body: content
                        }).then(resp => {
                            if (resp.ok) {
                                document.body.innerHTML = '<div class="card"><h2 style="color:#4ade80">✅ 推送成功！</h2><p>电视正在实时解析并播放您上传的频道列表，您可以返回电视观看！</p><button onclick="location.reload()">继续上传其他文件</button></div>';
                            } else {
                                alert('推送失败，请重试');
                            }
                        }).catch(err => {
                            alert('连接电视失败: ' + err);
                        });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getSuccessHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>上传成功</title></head>
            <body style="background:#0f141c;color:#fff;text-align:center;padding:50px;font-family:sans-serif;">
                <h1 style="color:#4ade80">✅ M3U 播放列表已成功推送到电视！</h1>
                <p>电视端已接收并自动加载频道，请查看电视屏幕。</p>
            </body>
            </html>
        """.trimIndent()
    }
}
