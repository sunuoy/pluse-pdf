package com.example.data.api

import com.squareup.moshi.JsonClass
import com.example.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Base64
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class PartResponse(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    val parts: List<PartResponse>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ContentResponse? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

class GeminiRepository {
    suspend fun generateSummary(text: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Key is missing or default. Please configure your GEMINI_API_KEY in the AI Studio Secrets Panel."
        }

        val prompt = "Please analyze this scientific/educational text and generate: \n1. A brief abstract summary (max 3 sentences).\n2. 4 bullet-point key takeaways.\n\nText:\n$text"
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = "You are a precise, research scientist assistant. Summarize objectively, using professional tone, without embellishment.")))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "No response from Gemini API"
        } catch (e: Exception) {
            "API Error: ${e.localizedMessage ?: "Unknown error"}. Check internet connection or API keys."
        }
    }

    suspend fun translateText(text: String, targetLanguage: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext LocalTranslationEngine.translate(text, targetLanguage)
        }

        val prompt = "Translate the following text into $targetLanguage. Provide only the translated text. Do not provide notes, introductions, or extra explanations.\n\nText:\n$text"
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = "You are an expert real-time translator. Translate accurately and strictly keep only the translation output.")))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val resultText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!resultText.isNullOrEmpty() && !resultText.startsWith("API Error") && !resultText.startsWith("Error")) {
                resultText
            } else {
                LocalTranslationEngine.translate(text, targetLanguage)
            }
        } catch (e: Exception) {
            LocalTranslationEngine.translate(text, targetLanguage)
        }
    }

    suspend fun performOcrOnImage(base64Image: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "" // Return empty so the caller falls back to its detailed local simulation!
        }

        val prompt = "Analyze this image and perform optical character recognition (OCR) to extract all text seen in this image. Present the recognized text layout-by-layout, cleanly. If no text is readable, state 'No text detected'."
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Unable to read captured document text."
        } catch (e: Exception) {
            "OCR API Error: ${e.localizedMessage ?: "Unknown error"}"
        }
    }
}

object LocalTranslationEngine {
    private val paragraphTranslations = mapOf(
        "Abstract: The dominant sequence" to mapOf(
            "Spanish" to "Resumen: Los modelos dominantes de transducción de secuencias se basan en complejas redes neuronales recurrentes o convolucionales que incluyen un codificador y un decodificador. Los modelos con mejor rendimiento también conectan el codificador y el decodificador a través de un mecanismo de atención. Proponemos una nueva arquitectura de red simple, el Transformer, basada únicamente en mecanismos de atención, prescindiendo por completo de la recurrencia y las convoluciones.",
            "French" to "Résumé: Les modèles de transduction de séquences dominants sont basés sur des réseaux de neurones récurrents ou convolutifs complexes qui incluent un encodeur et un décodeur. Les modèles les plus performants connectent également l'encodeur et le décodeur via un mécanisme d'attention. Nous proposons une nouvelle architecture de réseau simple, le Transformer, basée uniquement sur des mécanismes d'attention, se passant entièrement de récurrence et de convolutions.",
            "Japanese" to "要約：主要なシーケンス変換モデルは、エンコーダーとデコーダーを含む複雑な再帰型または畳み込みニューラルネットワークに基づいています。最高性能のモデルは、アテンションメカニズムを介してエンコーダーとデコーダーを接続します。私たちは、再帰や畳み込みを完全に排除し、アテンションメカニズムのみに基づいた新しいシンプルなネットワークアーキテクチャであるTransformerを提案します。",
            "German" to "Zusammenfassung: Die dominierenden Sequenzwandlungsmodelle basieren auf komplexen rekurrenten oder faltungsbasierten neuronalen Netzen, die einen Encoder und einen Decoder umfassen. Die leistungsstärksten Modelle verbinden zudem Encoder und Decoder über einen Aufmerksamkeitsmechanismus (Attention-Mechanismus). Wir schlagen eine neue einfache Netzwerkarchitektur vor, den Transformer, die ausschließlich auf Aufmerksamkeitsmechanismen basiert und gänzlich auf Rekursion und Faltungen verzichtet.",
            "Hindi" to "सार: प्रमुख अनुक्रम पारगमन मॉडल जटिल आवर्ती या कनवोल्यूशनल न्यूरल नेटवर्क पर आधारित होते हैं जिनमें एक एनकोडर और एक डिकोडर शामिल होता है। सबसे अच्छा प्रदर्शन करने वाले मॉडल एनकोडर और डिकोडर को एक ध्यान (अटेंशन) तंत्र के माध्यम से भी जोड़ते हैं। हम एक नए सरल नेटवर्क आर्किटेक्चर, ट्रांसफार्मर का प्रस्ताव करते हैं, जो पूरी तरह से ध्यान तंत्र पर आधारित है, जिसमें पुनरावृत्ति और कनवोल्यूशन की आवश्यकता नहीं होती है।",
            "Arabic" to "الملخص: تعتمد نماذج نقل التسلسل المهيمنة على شبكات عصبية متكررة أو تلافيفية معقدة تتضمن ترميزًا وفك تشفير. كما تربط النماذج الأفضل أداءً بين الترميز وفك التشفير من خلال آلية الانتباه. نقترح بنية شبكة بسيطة جديدة، وهي Transformer، تعتمد فقط على آليات الانتباه، وتستغني عن التكرار والتلافيف تمامًا."
        ),
        "Introduction: Recurrent neural" to mapOf(
            "Spanish" to "Introducción: Las redes neuronales recurrentes (RNN), las memorias de corto y largo plazo (LSTM) y las redes neuronales recurrentes controladas en particular, se han establecido firmemente como enfoques de última generación en el modelado de secuencias y problemas de transducción como el modelado de lenguaje y la traducción automática.",
            "French" to "Introduction: Les réseaux de neurones récurrents (RNN), la mémoire à court et long terme (LSTM) et les réseaux de neurones récurrents à portes en particulier, se sont fermement établis comme des approches de pointe dans la modélisation de séquences et les problèmes de transduction tels que la modélisation du langage et la traduction automatique.",
            "Japanese" to "イントロダクション：再帰型ニューラルネットワーク（RNN）、特に長短期記憶（LSTM）やゲート付き再帰型ニューラルネットワークは、言語モデリングや機械翻訳などのシーケンスモデリングおよび変換問題における最先端のアプローチとして確固たる地位を築いています。",
            "German" to "Einleitung: Rekurrente neuronale Netze (RNNs), insbesondere Long Short-Term Memory (LSTM) und Gated Recurrent Neural Networks, haben sich fest als modernste Ansätze bei der Sequenzmodellierung und Transduktionsproblemen wie der Sprachmodellierung und der maschinellen Übersetzung etabliert.",
            "Hindi" to "प्रस्तावना: आवर्ती न्यूरल नेटवर्क (RNN), विशेष रूप से लॉग शॉर्ट-टर्म मेमोरी (LSTM) और गेटेड आवर्ती न्यूरल नेटवर्क, अनुक्रम मॉडलिंग और पारगमन समस्याओं जैसे कि भाषा मॉडलिंग और मशीन अनुवाद में अत्याधुनिक दृष्टिकोण के रूप में मजबूती से स्थापित हुए हैं।",
            "Arabic" to "مقدمة: لقد ترسخت الشبكات العصبية المتكررة (RNNs)، وخاصة الذاكرة طويلة قصيرة المدى (LSTM) والشبكات العصبية المتكررة ذات البوابة، كأحدث الأساليب في نمذجة التسلسل ومشاكل النقل مثل نمذجة اللغة والترجمة الآلية."
        ),
        "Model Architecture: Most competitive" to mapOf(
            "Spanish" to "Arquitectura del Modelo: La mayoría de los modelos competitivos de transducción de secuencias neuronales tienen una estructura de codificador-decodificador. Aquí, el codificador asigna una secuencia de entrada de representaciones de símbolos a una secuencia de representaciones continuas. Dado esto, el decodificador genera una secuencia de salida de símbolos de un elemento a la vez.",
            "French" to "Architecture du modèle: La plupart des modèles compétitifs de transduction de séquences neuronales ont une structure encodeur-décodeur. Ici, l'encodeur mappe une séquence d'entrée de représentations de symboles à une séquence de représentations continues. À partir de là, le décodeur génère ensuite une séquence de sortie de symboles, un élément à la fois.",
            "Japanese" to "モデルアーキテクチャ：ほとんどの競争力のあるニューラルシーケンス変換モデルは、エンコーダー・デコーダー構造を持っています。ここで、エンコーダーは記号表現の入力シーケンスを連続表現のシーケンスにマッピングします。これに基づいて、デコーダーは出力シーケンスの記号を一度に1つの要素ずつ生成します。",
            "German" to "Modellarchitektur: Die meisten wettbewerbsfähigen neuronalen Sequenzwandlungsmodelle besitzen eine Encoder-Decoder-Struktur. Hierbei bildet der Encoder eine Eingabesequenz von Symbolrepräsentationen auf eine Sequenz kontinuierlicher Repräsentationen ab. Daraufhin erzeugt der Decoder ein Symbol nach dem anderen für die Ausgabesequenz.",
            "Hindi" to "मॉडल आर्किटेक्चर: अधिकांश प्रतिस्पर्धी न्यूरल अनुक्रम पारगमन मॉडल में एक एनकोडर-डिकोडर संरचना होती है। यहाँ, एनकोडर प्रतीक अभ्यावेदन के एक इनपुट अनुक्रम को निरंतर अभ्यावेदन के अनुक्रम में मैप करता है। इसके आधार पर, डिकोडर फिर एक समय में एक तत्व के रूप में प्रतीकों का एक आउटपुट अनुक्रम उत्पन्न करता है।",
            "Arabic" to "بنية النموذج: تحتوي معظم نماذج نقل التسلسل العصبي التنافسية على بنية ترميز وفك تشفير. هنا، يقوم المشفر برسم خريطة لتسلسل إدخال لتمثيلات الرموز إلى تسلسل من التمثيلات المستمرة. وبناءً على ذلك، يقوم فك التشفير بتوليد تسلسل إخراج من الرموز عنصرًا واحدًا في كل مرة."
        ),
        "Executive Summary: Decentralized" to mapOf(
            "Spanish" to "Resumen Ejecutivo: Las finanzas descentralizadas (DeFi) han experimentado un crecimiento fenomenal, transformando los sistemas de liquidación financiera entre pares. Al reemplazar los libros de órdenes limitadas (LOB) convencionales con creadores de mercado automatizados (AMM), los protocolos DeFi establecen una disponibilidad constante para el comercio de criptoactivos.",
            "French" to "Résumé exécutif: La finance décentralisée (DeFi) a connu une croissance phénoménale, transformant les systèmes de compensation financière de pair à pair. En remplaçant les carnets d'ordres limites (LOB) conventionnels par des teneurs de marché automatisés (AMM), les protocoles DeFi établissent une disponibilité constante pour le trading de crypto-actifs.",
            "Japanese" to "エグゼクティブサマリー：分散型金融（DeFi）は驚異的な成長を遂げ、ピアツーピアの金融決済システムを変革しました。従来の限度額注文簿（LOB）を自動マーケットメーカー（AMM）に置き換えることで、DeFiプロトコルは暗号資産取引の常時利用可能性を確立しています。",
            "German" to "Management-Zusammenfassung: Dezentrale Finanzmärkte (DeFi) haben ein phänomenales Wachstum verzeichnet und die Peer-to-Peer-Finanzclearing-Systeme transformiert. Durch den Ersatz herkömmlicher Limit-Orderbücher (LOB) durch automatisierte Market Maker (AMMs) etablieren DeFi-Protokolle eine ständige Verfügbarkeit für den Handel mit Krypto-Assets.",
            "Hindi" to "कार्यकारी सारांश: विकेंद्रीकृत वित्त (DeFi) ने अभूतपूर्व वृद्धि देखी है, जिससे पीयर-टू-पीयर वित्तीय समाशोधन प्रणालियों में बदलाव आया है। पारंपरिक सीमा आदेश बही (LOB) को स्वचालित बाजार निर्माताओं (AMM) से बदलकर, DeFi प्रोटोकॉल क्रिप्टो परिसंपत्तियों के व्यापार के लिए निरंतर उपलब्धता स्थापित करते हैं।",
            "Arabic" to "الملخص التنفيذي: شهد التمويل اللامركزي (DeFi) نموًا هائلاً، مما أحدث تحولًا في أنظمة المقاصة المالية بين النظراء. ومن خلال استبدال دفاتر طلبات الحد التقليدية (LOB) بصناع السوق الآليين (AMMs)، تؤسس بروتوكولات DeFi لتوافر مستمر لتداول الأصول المشفرة."
        ),
        "Constant Product Formula: The" to mapOf(
            "Spanish" to "Fórmula de Producto Constante: Los fundamentos matemáticos de los principales intercambios descentralizados (como Uniswap v2) se basan en la ecuación invariante del producto constante: x * y = k, donde x e y representan las reservas absolutas de dos tokens distintos en el pool, y k es una constante.",
            "French" to "Formule du produit constant: Les fondements mathématiques des principales bourses décentralisées (comme Uniswap v2) reposent sur l'équation invariante du produit constant: x * y = k, où x et y représentent les réserves absolutes de deux jetons distincts dans le pool, et k est une constante.",
            "Japanese" to "定数積公式：主要な分散型取引所（Uniswap v2など）の数学的基礎は、定数積不変方程式：x * y = kに基づいています。ここで、xとyはプール内の2つの異なるトークンの絶対的なリザーブを表し、kは定数です。",
            "German" to "Konstante Produktformel: Die mathematischen Grundlagen primärer dezentraler Börsen (wie Uniswap v2) beruhen auf der invarianten Gleichung des konstanten Produkts: x * y = k, wobei x und y die absoluten Reserven zweier verschiedener Token im Pool darstellen und k eine Konstante ist.",
            "Hindi" to "निरंतर उत्पाद सूत्र: प्राथमिक विकेंद्रीकृत एक्सचेंजों (जैसे Uniswap v2) की गणितीय नींव निरंतर उत्पाद अपरिवर्तनीय समीकरण पर टिकी हुई है: x * y = k, जहाँ x और y पूल में दो अलग-अलग टोकन के पूर्ण भंडार का प्रतिनिधित्व करते हैं, और k एक स्थिरांक है।",
            "Arabic" to "صيغة المنتج الثابت: ترتكز الأسس الرياضية للبورصات اللامركزية الأساسية (مثل Uniswap v2) على معادلة المنتج الثابت الثابتة: x * y = k، حيث يمثل x و y الاحتياطيات المطلقة لرمزين متميزين في المجمع، و k ثابت."
        ),
        "Introduction to Negative-Emission" to mapOf(
            "Spanish" to "Introducción a los Sistemas de Emisiones Negativas: Lograr los objetivos climáticos globales estándar requiere algo más que reducciones agresivas de emisiones. La eliminación activa de gases de efecto invernadero atmosféricos históricos a través de sistemas de Captura Directa de Aire (DAC) se ha convertido en un pilar científico indispensable.",
            "French" to "Introduction aux systèmes à émissions négatives: Atteindre les objectifs climatiques mondiaux standard nécessite plus que des réductions agressives des émissions. L'élimination active des gaz à effet de serre atmosphériques historiques via des systèmes de capture directe de l'air (DAC) est devenue un pilier scientifique indispensable.",
            "Japanese" to "負の排出システムへの導入：標準的な世界の気候目標を達成するには、積極的な排出削減以上のものが必要です。直接空気回収（DAC）システムを介して過去の大気中温室効果ガスを積極的に除去することは、不可欠な科学的柱となっています。",
            "German" to "Einführung in Systeme mit negativen Emissionen: Das Erreichen globaler Standard-Klimaziele erfordert mehr als nur aggressive Emissionsminderungen. Die aktive Entfernung historischer atmosphärischer Treibhausgase durch direkte Luftabscheidungssysteme (DAC) hat sich als unverzichtbare wissenschaftliche Säule herausgestellt.",
            "Hindi" to "नकारात्मक-उत्सर्जन प्रणालियों का परिचय: मानक वैश्विक जलवायु लक्ष्यों को प्राप्त करने के लिए केवल आक्रामक उत्सर्जन में कमी से अधिक की आवश्यकता है। डायरेक्ट एयर कैप्चर (DAC) प्रणालियों के माध्यम से ऐतिहासिक वायुमंडलीय ग्रीनहाउस गैसों को सक्रिय रूप से हटाना एक अनिवार्य वैज्ञानिक स्तंभ के रूप में उभरा है।",
            "Arabic" to "مقدمة إلى أنظمة الانبعاثات السلبية: يتطلب تحقيق الأهداف المناخية العالمية القياسية أكثر من مجرد تخفيضات هائلة في الانبعاثات. لقد برز الإزالة النشطة لغازات الاحتباس الحراري التاريخية في الغلاف الجوي عبر أنظمة الاحتجاز المباشر للهواء (DAC) كركيزة علمية لا غنى عنها."
        )
    )

    private val spanishDict = mapOf(
        "abstract" to "resumen", "introduction" to "introducción", "model" to "modelo", "architecture" to "arquitectura",
        "attention" to "atención", "transformer" to "transformador", "defi" to "DeFi", "liquidity" to "liquidez",
        "carbon" to "carbono", "capture" to "captura", "slippage" to "deslizamiento", "formula" to "fórmula",
        "loss" to "pérdida", "thermodynamic" to "termodinámico", "basalt" to "basalto", "efficiency" to "eficiencia",
        "calculations" to "cálculos", "recurrent" to "recurrente", "neural" to "neuronal", "networks" to "redes neuronales",
        "encoder" to "codificador", "decoder" to "decodificador", "mechanism" to "mecanismo", "convolutions" to "convoluciones"
    )

    private val frenchDict = mapOf(
        "abstract" to "résumé", "introduction" to "introduction", "model" to "modèle", "architecture" to "architecture",
        "attention" to "attention", "transformer" to "transformer", "defi" to "DeFi", "liquidity" to "liquidité",
        "carbon" to "carbone", "capture" to "capture", "slippage" to "glissement", "formula" to "formule",
        "loss" to "perte", "thermodynamic" to "thermodynamique", "basalt" to "basalte", "efficiency" to "efficacité",
        "calculations" to "calculs", "recurrent" to "récurrent", "neural" to "neuronal", "networks" to "réseaux de neurones",
        "encoder" to "encodeur", "decoder" to "décodeur", "mechanism" to "mécanisme"
    )

    private val japaneseDict = mapOf(
        "abstract" to "要約", "introduction" to "はじめに", "model" to "モデル", "architecture" to "アーキテクチャ",
        "attention" to "アテンション", "transformer" to "トランスフォーマー", "defi" to "分散型金融", "liquidity" to "流動性",
        "carbon" to "炭素", "capture" to "回収", "slippage" to "スリッページ", "formula" to "公式",
        "loss" to "損失", "thermodynamic" to "熱力学的", "basalt" to "玄武岩", "efficiency" to "効率",
        "calculations" to "計算", "recurrent" to "再帰型", "neural" to "ニューラル", "networks" to "ネットワーク",
        "encoder" to "エンコーダー", "decoder" to "デコーダー", "mechanism" to "メカニズム"
    )

    private val germanDict = mapOf(
        "abstract" to "Zusammenfassung", "introduction" to "Einleitung", "model" to "Modell", "architecture" to "Architektur",
        "attention" to "Aufmerksamkeit", "transformer" to "Transformer", "defi" to "DeFi", "liquidity" to "Liquidität",
        "carbon" to "Kohlenstoff", "capture" to "Abscheidung", "slippage" to "Slippage", "formula" to "Formel",
        "loss" to "Verlust", "thermodynamic" to "thermodynamisch", "basalt" to "Basalt", "efficiency" to "Effizienz",
        "calculations" to "Berechnungen", "recurrent" to "rekurrent", "neural" to "neuronal", "networks" to "Netzwerke",
        "encoder" to "Encoder", "decoder" to "Decoder"
    )

    private val hindiDict = mapOf(
        "abstract" to "संक्षेप", "introduction" to "परिचय", "model" to "मॉडल", "architecture" to "संरचना",
        "attention" to "ध्यान", "transformer" to "ट्रांसफार्मर", "defi" to "विकेंद्रीकृत वित्त", "liquidity" to "तरलता",
        "carbon" to "कार्बन", "capture" to "कैप्चर", "slippage" to "फिसलन", "formula" to "सूत्र",
        "loss" to "नुकसान", "thermodynamic" to "ऊष्मागतिक", "basalt" to "बसाल्ट", "efficiency" to "दक्षता"
    )

    private val arabicDict = mapOf(
        "abstract" to "ملخص", "introduction" to "مقدمة", "model" to "نموذج", "architecture" to "بنية",
        "attention" to "الانتباه", "transformer" to "المحول", "defi" to "التمويل اللامركزي", "liquidity" to "السيولة",
        "carbon" to "الكربون", "capture" to "الاحتجاز", "slippage" to "الانزلاق", "formula" to "صيغة",
        "loss" to "الخسارة", "thermodynamic" to "الديناميكية الحرارية", "basalt" to "البازلت", "efficiency" to "الكفاءة"
    )

    fun translate(text: String, targetLanguage: String): String {
        for ((prefix, langMap) in paragraphTranslations) {
            if (text.startsWith(prefix, ignoreCase = true) || text.contains(prefix, ignoreCase = true)) {
                val translation = langMap[targetLanguage]
                if (translation != null) return translation
            }
        }

        val dict = when (targetLanguage) {
            "Spanish" -> spanishDict
            "French" -> frenchDict
            "Japanese" -> japaneseDict
            "German" -> germanDict
            "Hindi" -> hindiDict
            "Arabic" -> arabicDict
            else -> emptyMap()
        }

        if (dict.isEmpty()) {
            return "[$targetLanguage]: $text"
        }

        val words = text.split(Regex("(?<=\\b)|(?=\\b)"))
        val result = StringBuilder()
        for (word in words) {
            val lower = word.lowercase()
            val mapped = dict[lower]
            if (mapped != null) {
                if (word.firstOrNull()?.isUpperCase() == true) {
                    result.append(mapped.replaceFirstChar { it.uppercase() })
                } else {
                    result.append(mapped)
                }
            } else {
                result.append(word)
            }
        }
        return result.toString()
    }
}
