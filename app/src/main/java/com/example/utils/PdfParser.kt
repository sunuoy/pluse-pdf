package com.example.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.PdfPageModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream
import java.util.regex.Pattern

object PdfParser {
    private const val TAG = "PdfParser"

    fun parsePdf(context: Context, uri: Uri): List<PdfPageModel> {
        val pages = mutableListOf<PdfPageModel>()
        try {
            val contentResolver = context.contentResolver
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
            
            // Find streams
            val streams = findStreams(bytes)
            Log.d(TAG, "Found ${streams.size} streams in PDF")

            var pageNum = 1
            for (stream in streams) {
                val decompressed = decompressStream(stream, bytes)
                if (decompressed != null) {
                    val text = extractTextFromPdfStream(decompressed)
                    if (text.trim().isNotEmpty()) {
                        // Split into paragraphs (group short lines, or split by double newlines)
                        val paragraphs = splitIntoParagraphs(text)
                        if (paragraphs.isNotEmpty()) {
                            pages.add(PdfPageModel(pageNumber = pageNum++, paragraphs = paragraphs))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing PDF", e)
        }
        return pages
    }

    private class StreamRange(val start: Int, val end: Int, val dictStart: Int, val dictEnd: Int)

    private fun findStreams(bytes: ByteArray): List<StreamRange> {
        val ranges = mutableListOf<StreamRange>()
        val size = bytes.size
        var i = 0
        while (i < size - 6) {
            // Check for "stream" keyword
            if (bytes[i] == 's'.toByte() &&
                bytes[i+1] == 't'.toByte() &&
                bytes[i+2] == 'r'.toByte() &&
                bytes[i+3] == 'e'.toByte() &&
                bytes[i+4] == 'a'.toByte() &&
                bytes[i+5] == 'm'.toByte()) {
                
                // Stream contents start after "stream\r\n" or "stream\n"
                var streamStart = i + 6
                if (streamStart < size && bytes[streamStart] == '\r'.toByte()) streamStart++
                if (streamStart < size && bytes[streamStart] == '\n'.toByte()) streamStart++
                
                // Search for "endstream"
                var streamEnd = -1
                var j = streamStart
                while (j < size - 9) {
                    if (bytes[j] == 'e'.toByte() &&
                        bytes[j+1] == 'n'.toByte() &&
                        bytes[j+2] == 'd'.toByte() &&
                        bytes[j+3] == 's'.toByte() &&
                        bytes[j+4] == 't'.toByte() &&
                        bytes[j+5] == 'r'.toByte() &&
                        bytes[j+6] == 'e'.toByte() &&
                        bytes[j+7] == 'a'.toByte() &&
                        bytes[j+8] == 'm'.toByte()) {
                        streamEnd = j
                        break
                    }
                    j++
                }
                
                if (streamEnd != -1) {
                    // Find preceding object dictionary starts with "<<" and ends before "stream"
                    var dictStart = -1
                    var dictEnd = -1
                    var k = i - 1
                    // Look back up to 500 bytes for "<<"
                    val lookbackLimit = maxOf(0, i - 500)
                    while (k >= lookbackLimit) {
                        if (k < size - 1 && bytes[k] == '<'.toByte() && bytes[k+1] == '<'.toByte()) {
                            dictStart = k
                            break
                        }
                        k--
                    }
                    if (dictStart != -1) {
                        // Find ">>" after dictStart
                        var m = dictStart
                        while (m < i) {
                            if (m < size - 1 && bytes[m] == '>'.toByte() && bytes[m+1] == '>'.toByte()) {
                                dictEnd = m + 2
                                break
                            }
                            m++
                        }
                    }
                    
                    ranges.add(StreamRange(streamStart, streamEnd, dictStart, dictEnd))
                    i = streamEnd + 9
                    continue
                }
            }
            i++
        }
        return ranges
    }

    private fun decompressStream(range: StreamRange, pdfBytes: ByteArray): ByteArray? {
        val streamBytes = pdfBytes.copyOfRange(range.start, range.end)
        if (range.dictStart == -1 || range.dictEnd == -1) {
            // If no dictionary found, try decompressing as Flate anyway, or return raw
            return tryDecompress(streamBytes) ?: streamBytes
        }
        
        val dictText = String(pdfBytes, range.dictStart, range.dictEnd - range.dictStart, Charsets.US_ASCII)
        val isFlate = dictText.contains("/FlateDecode") || dictText.contains("/Flate")
        
        if (isFlate) {
            return tryDecompress(streamBytes)
        }
        return streamBytes
    }

    private fun tryDecompress(bytes: ByteArray): ByteArray? {
        return try {
            val iis = InflaterInputStream(ByteArrayInputStream(bytes))
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            var len: Int
            while (iis.read(buf).also { len = it } > 0) {
                bos.write(buf, 0, len)
            }
            bos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractTextFromPdfStream(streamBytes: ByteArray): String {
        // PDF page content instructions:
        // text can be in (string) Tj or [(str1) num (str2)] TJ
        val content = String(streamBytes, Charsets.UTF_8)
        val sb = java.lang.StringBuilder()
        
        // Match string constants: ( ... )
        val pattern = Pattern.compile("\\(([^)]*)\\)")
        val matcher = pattern.matcher(content)
        while (matcher.find()) {
            val matchedStr = matcher.group(1) ?: continue
            // Exclude common PDF keys or layout parameters that look like text
            if (matchedStr.trim().isEmpty()) continue
            if (matchedStr.startsWith("/") || matchedStr.length < 2) continue
            sb.append(matchedStr).append(" ")
        }
        return sb.toString()
    }

    private fun splitIntoParagraphs(text: String): List<String> {
        // Clean up text
        val cleaned = text
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\r", "")
            .replace("\\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            
        if (cleaned.isEmpty()) return emptyList()
        
        // Let's divide into reasonable paragraphs of ~3-5 sentences each
        val sentences = cleaned.split(Regex("(?<=[.!?])\\s+"))
        val paragraphs = mutableListOf<String>()
        var currentParagraph = java.lang.StringBuilder()
        var sentenceCount = 0
        
        for (sentence in sentences) {
            if (sentence.trim().isEmpty()) continue
            currentParagraph.append(sentence).append(" ")
            sentenceCount++
            if (sentenceCount >= 4 || sentence.contains("Abstract:") || sentence.contains("Summary:") || sentence.contains("Conclusion:")) {
                paragraphs.add(currentParagraph.toString().trim())
                currentParagraph = java.lang.StringBuilder()
                sentenceCount = 0
            }
        }
        if (currentParagraph.isNotEmpty()) {
            paragraphs.add(currentParagraph.toString().trim())
        }
        return paragraphs
    }

    fun serializePages(pages: List<PdfPageModel>): String {
        val sb = java.lang.StringBuilder()
        sb.append("[")
        pages.forEachIndexed { i, page ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"pageNumber\":").append(page.pageNumber).append(",")
            sb.append("\"paragraphs\":[")
            page.paragraphs.forEachIndexed { j, para ->
                if (j > 0) sb.append(",")
                val escaped = para.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                sb.append("\"").append(escaped).append("\"")
            }
            sb.append("]")
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    fun deserializePages(json: String): List<PdfPageModel> {
        val pages = mutableListOf<PdfPageModel>()
        try {
            val pageMatches = Regex("\\{\\s*\"pageNumber\"\\s*:\\s*(\\d+)\\s*,\\s*\"paragraphs\"\\s*:\\s*\\[(.*?)\\]\\s*\\}").findAll(json)
            for (match in pageMatches) {
                val pageNum = match.groupValues[1].toInt()
                val parasContent = match.groupValues[2]
                val paragraphs = mutableListOf<String>()
                val paraMatches = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(parasContent)
                for (pMatch in paraMatches) {
                    val p = pMatch.groupValues[1]
                        .replace("\\\\", "\\")
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                    paragraphs.add(p)
                }
                pages.add(PdfPageModel(pageNum, paragraphs))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return pages
    }
}
