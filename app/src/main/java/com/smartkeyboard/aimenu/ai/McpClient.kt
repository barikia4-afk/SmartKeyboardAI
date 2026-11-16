package com.smartkeyboard.aimenu.ai

import com.smartkeyboard.aimenu.data.McpServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for user-configured Model Context Protocol (MCP) servers.
 * MCP is a standard tool and context protocol.
 * ALL tool invocations require explicit user confirmation.
 */
class McpClient(private val serverConfig: McpServerConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Test connection to MCP server endpoint.
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (serverConfig.endpointUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Endpoint URL cannot be empty"))
        }

        try {
            val listToolsResult = listAvailableTools()
            if (listToolsResult.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(listToolsResult.exceptionOrNull() ?: IOException("MCP Ping failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Query tools/list from MCP server.
     */
    suspend fun listAvailableTools(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val rpcRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", "list-tools-1")
                put("method", "tools/list")
                put("params", JSONObject())
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = rpcRequest.toString().toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url(serverConfig.endpointUrl)
                .post(body)

            if (serverConfig.authHeader.isNotBlank()) {
                requestBuilder.addHeader("Authorization", serverConfig.authHeader)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: MCP Server error"))
                }

                val respStr = response.body?.string() ?: "{}"
                val json = JSONObject(respStr)
                val resultObj = json.optJSONObject("result")
                val toolsArray = resultObj?.optJSONArray("tools") ?: JSONArray()

                val toolsList = mutableListOf<String>()
                for (i in 0 until toolsArray.length()) {
                    val tool = toolsArray.getJSONObject(i)
                    toolsList.add(tool.optString("name", "tool_$i"))
                }

                Result.success(toolsList)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Invoke an MCP tool with enforced explicit confirmation.
     * Never runs automatically!
     */
    suspend fun invokeToolWithConfirmation(
        toolName: String,
        arguments: JSONObject,
        onUserConfirm: suspend () -> Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        // Enforce user confirmation before any network or local tool execution
        val isConfirmed = onUserConfirm()
        if (!isConfirmed) {
            return@withContext Result.failure(SecurityException("Tool invocation cancelled by user."))
        }

        try {
            val rpcRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", "call-tool-${System.currentTimeMillis()}")
                put("method", "tools/call")
                put("params", JSONObject().apply {
                    put("name", toolName)
                    put("arguments", arguments)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = rpcRequest.toString().toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url(serverConfig.endpointUrl)
                .post(body)

            if (serverConfig.authHeader.isNotBlank()) {
                requestBuilder.addHeader("Authorization", serverConfig.authHeader)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code} executing tool"))
                }

                val respStr = response.body?.string() ?: "{}"
                val json = JSONObject(respStr)
                val resultObj = json.optJSONObject("result")
                val contentArray = resultObj?.optJSONArray("content")
                val text = if (contentArray != null && contentArray.length() > 0) {
                    contentArray.getJSONObject(0).optString("text", "")
                } else {
                    resultObj?.toString() ?: "Done"
                }

                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
