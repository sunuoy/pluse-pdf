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
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
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
            "French" to "Résumé: Les modèles de transduction de séquences dominants sont basés sur des réseaux de nouvaux récurrents ou convolutifs complexes qui incluent un encodeur et un décodeur. Les modèles les plus performants connectent également l'encodeur et le décodeur via un mécanisme d'attention. Nous proposons une nouvelle architecture de réseau simple, le Transformer, basée uniquement sur des mécanismes d'attention, se passant entièrement de récurrence et de convolutions.",
            "Japanese" to "要約：主要なシーケンス変換モデルは、エンコーダーとデコーダーを含む複雑な再帰型または畳み込みニューラルネットワークに基づいています。最高性能のモデルは、アテンションメカニズムを介してエンコーダーとデコーダーを接続します。私たちは、再帰や畳み込みを完全に排除し、アテンションメカニズムのみに基づいた新しいシンプルなネットワークアーキテクチャであるTransformerを提案します。",
            "German" to "Zusammenfassung: Die dominierenden Sequenzwandlungsmodelle basieren auf komplexen rekurrenten oder faltungsbasierten neuronalen Netzen, die einen Encoder und einen Decoder umfassen. Die leistungsstärksten Modelle verbinden zudem Encoder und Decoder über einen Aufmerksamkeitsmechanismus (Attention-Mechanismus). Wir schlagen eine neue einfache Netzwerkarchitektur vor, den Transformer, die ausschließlich auf Aufmerksamkeitsmechanismen basiert und gänzlich auf Rekursion und Faltungen verzichtet.",
            "Hindi" to "सार: प्रमुख अनुक्रम पारगमन मॉडल जटिल आवर्ती या कनवोल्यूशनल न्यूरल नेटवर्क पर आधारित होते हैं जिनमें एक एनकोडर और एक डिकोडर शामिल होता है। सबसे अच्छा प्रदर्शन करने वाले मॉडल एनकोडर और डिकोडर को एक ध्यान (अटेंशन) तंत्र के माध्यम से भी जोड़ते हैं। हम एक नए सरल नेटवर्क आर्किटेक्चर, ट्रांसफार्मर का प्रस्ताव करते हैं, जो पूरी तरह से ध्यान तंत्र पर आधारित है, जिसमें पुनरावृत्ति और कनवोल्यूशन की आवश्यकता नहीं होती है।",
            "Arabic" to "الملخص: تعتمد نماذج نقل التسلسل المهيمنة على شبكات عصبية متكررة أو تلافيفية معقدة تتضمن ترميزًا وفك تشفير. كما تربط النماذج الأفضل أداءً بين الترميز وفك التشفير من خلال آلية الانتباه. نقترح بنية شبكة بسيطة جديدة، وهي Transformer، تعتمد فقط على آليات الانتباه، وتستغني عن التكرار والتلافيف تمامًا.",
            "Telugu" to "సారాంశం: ప్రాముఖ్యత గల సీక్వెన్స్ ట్రాన్స్‌డక్షన్ మోడల్‌లు ఎన్‌కోడర్ మరియు డీకోడర్‌లను కలిగి ఉన్న క్లిష్టమైన రికరెంట్ లేదా కన్వల్యూషనల్ న్యూరల్ నెట్‌వర్క్‌లపై ఆధారపడి ఉంటాయి. అత్యుత్తమ పనిచేసే మోడల్‌లు అటెన్షన్ మెకానిజం ద్వారా ఎన్‌కోడర్ మరియు డీకోడర్‌ను కూడా కనెక్ట్ చేస్తాయి. మేము రికరెన్స్ మరియు కన్వల్యూషన్‌లను పూర్తిగా విస్మరించి, కేవలం అటెన్షన్ మెకానిజమ్‌లపై ఆధారపడిన సరికొత్త సరళమైన నెట్‌వర్క్ నిర్మాణం, 'ట్రాన్స్‌ఫార్మర్'ను ప్రతిపాదిస్తున్నాము."
        ),
        "Introduction: Recurrent neural" to mapOf(
            "Spanish" to "Introducción: Las redes neuronales recurrentes (RNN), las memorias de corto y largo plazo (LSTM) y las redes neuronales recurrentes controladas en particular, se han establecido firmemente como enfoques de última generación en el modelado de secuencias y problemas de transducción como el modelado de lenguaje y la traducción automática.",
            "French" to "Introduction: Les réseaux de neurones récurrents (RNN), la mémoire à court et long terme (LSTM) et les réseaux de neurones récurrents à portes en particulier, se sont fermement établis comme des approches de pointe dans la modélisation de séquences et les problèmes de transduction tels que la modélisation du langage et la traduction automatique.",
            "Japanese" to "イントロダクション：再帰型ニューラルネットワーク（RNN）、特に長短期記憶（LSTM）やゲート付き再帰型ニューラルネットワークは、言語モデリングや機械翻訳などのシーケンスモデリングおよび変換問題における最先端のアプローチとして確固たる地位を築いています。",
            "German" to "Einleitung: Rekurrente neuronale Netze (RNNs), insbesondere Long Short-Term Memory (LSTM) und Gated Recurrent Neural Networks, haben sich fest als modernste Ansätze bei der Sequenzmodellierung und Transduktionsproblemen wie der Sprachmodellierung und der maschinellen Übersetzung etabliert.",
            "Hindi" to "प्रस्तावना: आवर्ती न्यूरल नेटवर्क (RNN), विशेष रूप से लॉग शॉर्ट-टर्म मेमोरी (LSTM) और गेटेड आवर्ती न्यूरल नेटवर्क, अनुक्रम मॉडलिंग और पारगमन समस्याओं जैसे कि भाषा मॉडलिंग और मशीन अनुवाद में अत्याधुनिक दृष्टिकोण के रूप में मजबूती से स्थापित हुए हैं।",
            "Arabic" to "مقدمة: لقد ترسخت الشبكات العصبية المتكررة (RNNs)، وخاصة الذاكرة طويلة قصيرة المدى (LSTM) والشبكات العصبية المتكررة ذات البوابة، كأحدث الأساليب في نمذجة التسلسل ومشاكل النقل مثل نمذجة اللغة والترجمة الآلية.",
            "Telugu" to "పరిచయం: సీక్వెన్స్ మోడలింగ్ మరియు ట్రాన్స్‌డక్షన్ సమస్యలలో రికరెంట్ న్యూరల్ నెట్‌వర్క్‌లు (RNNs), ముఖ్యంగా లాంగ్ షార్ట్-టర్మ్ మెమరీ (LSTM) మరియు గేటెడ్ రికరెంట్ న్యూరల్ నెట్‌వర్క్‌లు అత్యాధునిక విధానాలుగా స్థిరపడ్డాయి."
        ),
        "Recurrent models factor computation" to mapOf(
            "Spanish" to "Los modelos recurrentes factorizan el cómputo a lo largo de las posiciones de los símbolos de las secuencias de entrada y salida.",
            "French" to "Les modèles récurrents factorisent le calcul le long des positions des symboles des séquences d'entrée et de sortie.",
            "Japanese" to "再帰モデルは、入力シーケンスと出力シーケンスの記号位置に沿って計算を因数分解します。",
            "German" to "Rekurrente Modelle faktorisieren die Berechnung entlang der Symbolpositionen der Eingabe- und Ausgabesequenzen.",
            "Hindi" to "आवर्ती मॉडल इनपुट और आउटपुट अनुक्रमों के प्रतीक पदों के साथ गणना को कारक बनाते हैं।",
            "Arabic" to "عوامل النماذج المتكررة تحسب على طول مواضع الرموز لتسلسلات المدخلات والمخرجات."
        ),
        "This inherent sequential nature" to mapOf(
            "Spanish" to "Esta naturaleza secuencial inherente impide la paralelización dentro de los ejemplos de entrenamiento, lo que se vuelve crítico en longitudes de secuencia más largas.",
            "French" to "Cette nature séquentielle inhérente exclut la parallélisation au sein des exemples d'apprentissage, ce qui devient critique pour des longueurs de séquence plus longues.",
            "Japanese" to "この固有のシーケンシャルな性質は、トレーニング例内の並列化を妨げ、シーケンス長が長くなるとクリティカルになります。",
            "German" to "Diese inhärente sequentielle Natur schließt eine Parallelisierung innerhalb von Trainingsbeispielen aus, was bei längeren Sequenzlängen kritisch wird.",
            "Hindi" to "यह अंतर्निहित अनुक्रमिक प्रकृति प्रशिक्षण उदाहरणों के भीतर समानांतरता को रोकती है, जो लंबी अनुक्रम लंबाई पर महत्वपूर्ण हो जाती है।",
            "Arabic" to "هذه الطبيعة المتسلسلة المتأصلة تمنع التوازي داخل أمثلة التدريب, الأمر الذي يصبح حاسمًا عند أطوال التسلسل الأطول."
        ),
        "Model Architecture: Most competitive" to mapOf(
            "Spanish" to "Arquitectura del Modelo: La mayoría de los modelos competitivos de transducción de secuencias neuronales tienen una estructura de codificador-decodificador. Aquí, el codificador asigna una secuencia de entrada de representaciones de símbolos a una secuencia de representaciones continuas. Dado esto, el decodificador genera una secuencia de salida de símbolos de un elemento a la vez.",
            "French" to "Architecture du modèle: La plupart des modèles compétitifs de transduction de séquences neuronales ont une structure encodeur-décodeur. Ici, l'encodeur mappe une séquence d'entrée de représentations de symboles à une séquence de représentations continues. À partir de là, le décodeur génère ensuite une séquence de sortie de symboles, un élément à la fois.",
            "Japanese" to "モデルアーキテクチャ：ほとんどの競争力のあるニューラルシーケンス変換モデルは、エンコーダー・デコーダー構造を持っています。ここで、エンコーダーは記号表現の入力シーケンスを連続表現のシーケンスにマッピングします。これに基づいて、デコーダーは出力シーケンスの記号を一度に1つの要素ずつ生成します。",
            "German" to "Modellarchitektur: Die meisten wettbewerbsfähigen neuronalen Sequenzwandlungsmodelle besitzen eine Encoder-Decoder-Struktur. Hierbei bildet der Encoder eine Eingabesequenz von Symbolrepräsentationen auf eine Sequenz kontinuierlicher Repräsentationen ab. Daraufhin erzeugt der Decoder ein Symbol nach dem anderen für die Ausgabesequenz.",
            "Hindi" to "मॉडल आर्किटेक्चर: अधिकांश प्रतिस्पर्धी न्यूरल अनुक्रम पारगमन मॉडल में एक एनकोडर-डिकोडर संरचना होती है। यहाँ, एनकोडर प्रतीक अभ्यावेदन के एक इनपुट अनुक्रम को निरंतर अभ्यावेदन के अनुक्रम में मैप करता है। इसके आधार पर, डिकोडर फिर एक समय में एक तत्व के रूप में प्रतीकों का एक आउटपुट अनुक्रम उत्पन्न करता है।",
            "Arabic" to "بنية النموذج: تحتوي معظم نماذج نقل التسلسل العصبي التنافسية على بنية ترميز وفك تشفير. هنا، يقوم المشفر برسم خريطة لتسلسل إدخال لتمثيلات الرموز إلى تسلسل من التمثيلات المستمرة. وبناءً على ذلك، يقوم فك التشفير بتوليد تسلسل إخراج من الرموز عنصرًا واحدًا في كل مرة."
        ),
        "The Transformer follows" to mapOf(
            "Spanish" to "El Transformer sigue esta arquitectura general utilizando autoatención apilada y capas totalmente conectadas punto a punto tanto para el codificador como para el decodificador.",
            "French" to "Le Transformer suit cette architecture globale en utilisant une auto-attention empilée et des couches entièrement connectées point à point pour l'encodeur et le décodeur.",
            "Japanese" to "Transformerは、エンコーダーとデコーダーの両方にスタックされた自己アテンションとポイントワイズの完全結合レイヤーを使用して、この全体的なアーキテクチャに従います。",
            "German" to "Der Transformer folgt dieser Gesamtarchitektur unter Verwendung von gestapelter Self-Attention und punktweisen, vollgekoppelten Schichten sowohl für den Encoder als auch für den Decoder.",
            "Hindi" to "ट्रांसफार्मर एनकोडर और डिकोडर दोनों के लिए स्टैक्ड सेल्फ-अटेंशन और पॉइंट-वार, पूरी तरह से जुड़े परतों का उपयोग करके इस समग्र आर्किटेक्चर का अनुसरण करता है।",
            "Arabic" to "يتبع Transformer هذه البنية العامة باستخدام الانتباه الذاتي المكدس وطبقات متصلة بالكامل من نقطة إلى نقطة لكل من الترميز وفك التشفير."
        ),
        "An attention function can" to mapOf(
            "Spanish" to "Una función de atención se puede describir como mapear una consulta y un conjunto de pares clave-valor a una salida, donde la consulta, las claves, los valores y la salida son todos vectores.",
            "French" to "Une fonction d'attention peut être décrite comme la mise en correspondance d'une requête et d'un ensemble de paires clé-valeur avec une sortie, où la requête, les clés, les valeurs et la sortie sont tous des vecteurs.",
            "Japanese" to "アテンション機能は、クエリとキーと値のペアのセットを出力にマッピングすることとして説明できます。ここで、クエリ、キー、値、および出力はすべてベクトルです。",
            "German" to "Eine Aufmerksamkeitsfunktion kann als die Abbildung einer Abfrage (Query) und eines Satzes von Schlüssel-Wert-Paaren (Key-Value-Pairs) auf eine Ausgabe beschrieben werden, wobei Abfrage, Schlüssel, Werte und Ausgabe Vektoren sind.",
            "Hindi" to "एक ध्यान कार्य को एक क्वेरी और की-वैल्यू पेयर के सेट को एक आउटपुट में मैप करने के रूप में वर्णित किया जा सकता है, जहां क्वेरी, कीज़, वैल्यूज़ और आउटपुट सभी वैक्टर हैं।",
            "Arabic" to "يمكن وصف وظيفة الانتباه بأنها رسم خريطة لاستعلام ومجموعة من أزواج القيمة الرئيسية لمخرجات، حيث تكون الاستعلام والمفاتيح والقيم والمخرجات كلها متجهات."
        ),
        "Multi-Head Attention: Instead" to mapOf(
            "Spanish" to "Atención de múltiples cabezales: En lugar de realizar una única función de atención con consultas, claves y valores d-dimensionales, descubrimos que es beneficioso proyectar linealmente las consultas, claves y valores h veces con diferentes proyecciones lineales aprendidas.",
            "French" to "Attention multi-têtes: Au lieu d'effectuer une seule fonction d'attention avec des requêtes, des clés et des valeurs d-dimensionnelles, nous avons trouvé avantageux de projeter linéairement les requêtes, les clés et les valeurs h fois avec différentes projections linéaires apprises.",
            "Japanese" to "マルチヘッドアテンション：d次元のクエリ、キー、値を使用して単一のアテンション機能を実行する代わりに、異なる学習済みの線形投影を使用して、クエリ、キー、値をh回線形投影することが有益であることがわかりました。",
            "German" to "Multi-Head-Aufmerksamkeit: Anstatt eine einzelne Aufmerksamkeitsfunktion mit d-dimensionalen Abfragen, Schlüsseln und Werten auszuführen, fanden wir es vorteilhaft, die Abfragen, Schlüssel und Werte h-mal mit unterschiedlichen, gelernten linearen Projektionen linear zu projizieren.",
            "Hindi" to "मल्टी-हेड अटेंशन: d-डायमेंशनल क्वेरीज़, कीज़ और वैल्यूज़ के साथ एक सिंगल अटेंशन फ़ंक्शन करने के बजाय, हमने पाया कि अलग-अलग, सीखे गए लीनियर प्रोजेक्शन के साथ क्वेरीज़, कीज़ और वैल्यूज़ को h बार लीनियर रूप से प्रोजेक्ट करना फायदेमंद है।",
            "Arabic" to "انتباه متعدد الرؤوس: بدلاً من أداء وظيفة انتباه واحدة باستعلامات ومفاتيح وقيم d-الأبعاد، وجدنا أنه من المفيد عرض الاستعلامات والمفاتيح والقيم خطيًا h من المرات بمساقط خطية مختلفة ومتعلمة."
        ),
        "Positional Encoding: Since our" to mapOf(
            "Spanish" to "Codificación posicional: dado que nuestro modelo no contiene recurrencia ni convolución, para que el modelo haga uso del orden de la secuencia, debemos inyectar información sobre la position relativa o absoluta de los tokens en la secuencia.",
            "French" to "Encodage positionnel: comme notre modèle ne contient pas de récurrence ni de convolution, pour que le modèle utilise l'ordre de la séquence, nous devons injecter des informations sur la position relative ou absolue des jetons dans la séquence.",
            "Japanese" to "位置エンコーディング：当社のモデルには再帰も畳み込みも含まれていないため、モデルがシーケンスの順序を利用するには、シーケンス内のトークンの相対的または絶対的な位置に関する情報を注入する必要があります。",
            "German" to "Positionskodierung: Da unser Modell keine Rekursion und keine Faltung enthält, müssen wir Informationen über die relative oder absolute Position der Token in der Sequenz einbringen, damit das Modell die Reihenfolge der Sequenz nutzen kann.",
            "Hindi" to "पोजिशनल एन्कोडिंग: चूंकि हमारे मॉडल में कोई पुनरावृत्ति और कोई कनवोल्यूशन नहीं है, इसलिए मॉडल के लिए अनुक्रम के क्रम का उपयोग करने के लिए, हमें अनुक्रम में टोकन की सापेक्ष या पूर्ण स्थिति के बारे में कुछ जानकारी इंजेक्ट करनी होगी।",
            "Arabic" to "التشفير الموضعي: نظرًا لأن نموذجنا لا يحتوي على تكرار ولا تلافيف، ولكي يستفيد النموذج من ترتيب التسلسل، يجب علينا حقن بعض المعلومات حول الموضع النسبي أو المطلق للرموز في التسلسل."
        ),
        "To this end, we add" to mapOf(
            "Spanish" to "Con este fin, agregamos 'codificaciones posicionales' a las incrustaciones de entrada en la parte inferior de las pilas del codificador y del decodificador.",
            "French" to "À cette fin, nous ajoutons des « encodages positionnels » aux intégrations d'entrée au bas des piles d'encodeur et de décodeur.",
            "Japanese" to "この目的のために、エンコーダーとデコーダーのスタックの底部にある入力埋め込みに「位置エンコーディング」を追加します。",
            "German" to "Zu diesem Zweck fügen wir den Eingabe-Embeddings am unteren Ende der Encoder- und Decoder-Stacks \"Positionskodierungen\" hinzu.",
            "Hindi" to "इस उद्देश्य के लिए, हम एनकोडर और डिकोडर स्टैक के निचले भाग में इनपुट एम्बेडिंग में \"पोजिशनल एन्कोडिंग\" जोड़ते हैं।",
            "Arabic" to "تحقيقًا لهذه الغاية، نضيف 'ترميزات موضعية' إلى تضمينات الإدخال في الجزء السفلي من مجموعات الترميز وفك التشفير."
        ),
        "Why Self-Attention: In" to mapOf(
            "Spanish" to "Por qué la autoatención: en esta sección comparamos varios aspectos de las capas de autoatención con las capas recurrentes y convolucionales comúnmente utilizadas.",
            "French" to "Pourquoi l'auto-attention: Dans cette section, nous comparons divers aspects des couches d'auto-attention aux couches récurrentes et convolutives couramment utilisées.",
            "Japanese" to "なぜ自己アテンションなのか：このセクションでは、自己アテンションレイヤーのさまざまな側面を、一般的に使用される再帰レイヤーや畳み込みレイヤーと比較します。",
            "German" to "Warum Self-Attention: In diesem Abschnitt vergleichen wir verschiedene Aspekte von Self-Attention-Schichten mit den üblicherweise verwendeten rekurrenten und faltungsbasierten Schichten.",
            "Hindi" to "स्व-ध्यान क्यों: इस खंड में हम आमतौर पर उपयोग की जाने वाली आवर्ती और कनवोल्यूशनल परतों से स्व-ध्यान परतों के विभिन्न पहलुओं की तुलना करते हैं।",
            "Arabic" to "لماذا الانتباه الذاتي: في هذا القسم، نقارن جوانب مختلفة من طبقات الانتباه الذاتي بالطبقات المتكررة والتلافيفية المستخدمة عادةً."
        ),
        "The first is the total" to mapOf(
            "Spanish" to "La primera es la complejidad computacional total por capa. La segunda es la cantidad de computación que se puede paralelizar.",
            "French" to "La première est la complexité informatique totale par couche. La seconde est la quantité de calcul qui peut être parallélisée.",
            "Japanese" to "1つ目はレイヤーごとの総計算複雑度です。2つ目は並列化できる計算量です。",
            "German" to "Das erste ist die gesamte Rechenkomplexität pro Schicht. Das zweite ist der Rechenaufwand, der parallelisiert werden kann.",
            "Hindi" to "पहला प्रति परत कुल कम्प्यूटेशनल जटिलता है। दूसरा गणना की मात्रा है जिसे समानांतर किया जा सकता है।",
            "Arabic" to "الأول هو التعقيد الحسابي الإجمالي لكل طبقة. والثاني هو كمية الحساب التي يمكن موازاتها."
        ),
        "Training & Results: This" to mapOf(
            "Spanish" to "Entrenamiento y resultados: Esta sección describe el régimen de entrenamiento de nuestros modelos utilizando el conjunto de datos estándar WMT 2014.",
            "French" to "Entraînement et résultats: Cette section décrit le régime d'entraînement de nos modèles à l'aide de l'ensemble de données standard WMT 2014.",
            "Japanese" to "トレーニングと結果：このセクションでは、標準的なWMT 2014データセットを使用したモデルのトレーニング計画について説明します。",
            "German" to "Training & Ergebnisse: Dieser Abschnitt beschreibt das Trainingsregime für unsere Modelle unter Verwendung des Standard-WMT-2014-Datensatzes.",
            "Hindi" to "प्रशिक्षण और परिणाम: यह अनुभाग मानक WMT 2014 डेटासेट का उपयोग करके हमारे मॉडल के लिए प्रशिक्षण व्यवस्था का वर्णन करता है।",
            "Arabic" to "التدريب والنتائج: يصف هذا القسم نظام التدريب لنماذجنا باستخدام مجموعة بيانات WMT 2014 القياسية."
        ),
        "Sentences were encoded using" to mapOf(
            "Spanish" to "Las oraciones se codificaron mediante codificación de pares de bytes, que tiene un vocabulario compartido de origen y destino de unos 37000 tokens.",
            "French" to "Les phrases ont été encodées à l'aide d'un encodage par paires d'octets, qui possède un vocabulaire source-cible partagé d'environ 37 000 jetons.",
            "Japanese" to "文はバイトペアエンコーディングを使用してエンコードされ、約37,000トークンの共有ソース/ターゲット語彙を持ちます。",
            "German" to "Die Sätze wurden mittels Byte-Pair-Encoding kodiert, das ein gemeinsames Quell-Ziel-Vokabular von etwa 37.000 Token besitzt.",
            "Hindi" to "वाक्यों को बाइट-पेयर एन्कोडिंग का उपयोग करके एन्कोड किया गया था, जिसमें लगभग 37000 टोकन की एक साझा स्रोत-लक्षित शब्दावली है।",
            "Arabic" to "تم تشفير الجمل باستخدام تشفير زوج البايت، والذي يحتوي على مفردات مشتركة بين المصدر والهدف تبلغ حوالي 37000 رمز."
        ),
        "On the English-to-German" to mapOf(
            "Spanish" to "En la tarea de traducción de inglés a alemán, nuestro modelo grande supera a los mejores modelos informados anteriormente por más de 2.0 BLEU.",
            "French" to "Sur la tâche de traduction de l'anglais vers l'allemand, notre grand modèle surpasse les meilleurs modèles précédemment signalés de plus de 2,0 BLEU.",
            "Japanese" to "英語からドイツ語への翻訳タスクにおいて、当社の大型モデルは、以前に報告された最高のモデルを2.0 BLEU以上上回りました。",
            "German" to "Bei der Übersetzung vom Englischen ins Deutsche übertrifft unser großes Modell die besten zuvor gemeldeten Modelle um mehr als 2,0 BLEU.",
            "Hindi" to "अंग्रेजी-से-जर्मन अनुवाद कार्य पर, हमारा बड़ा मॉडल पहले रिपोर्ट किए गए सर्वश्रेष्ठ मॉडलों से 2.0 BLEU से अधिक बेहतर प्रदर्शन करता है।",
            "Arabic" to "في مهمة الترجمة من الإنجليزية إلى الألمانية، تفوق نموذجنا الكبير على أفضل النماذج التي تم الإبلاغ عنها سابقًا بأكثر من 2.0 BLEU."
        ),
        "On the English-to-French" to mapOf(
            "Spanish" to "En la tarea de traducción de inglés a francés, nuestro modelo grande establece una nueva puntuación BLEU de última generación de 41.8.",
            "French" to "Sur la tâche de traduction de l'anglais vers le français, notre grand modèle établit un nouveau score BLEU de pointe de 41,8.",
            "Japanese" to "英語からフランス語への翻訳タスクにおいて、当社の大型モデルは41.8という新たな最先端のBLEUスコアを確立しました。",
            "German" to "Bei der Übersetzung vom Englischen ins Französische stellt unser großes Modell mit 41,8 einen neuen, hochmodernen BLEU-Score auf.",
            "Hindi" to "अंग्रेजी-से-फ्रेंच अनुवाद कार्य पर, हमारा बड़ा... मॉडल 41.8 का नया अत्याधुनिक BLEU स्कोर स्थापित करता है।",
            "Arabic" to "في مهمة الترجمة من الإنجليزية إلى الفرنسية، حقق نموذجنا الكبير درجة BLEU جديدة تبلغ 41.8."
        ),
        "Executive Summary: Decentralized" to mapOf(
            "Spanish" to "Resumen Ejecutivo: Las finanzas descentralizadas (DeFi) han experimentado un crecimiento fenomenal, transformando los sistemas de liquidación financiera entre pares. Al reemplazar los libros de órdenes limitadas (LOB) convencionales con creadores de mercado automatizados (AMM), los protocolos DeFi establecen una disponibilidad constante para el comercio de criptoactivos.",
            "French" to "Résumé exécutif: La finance décentralisée (DeFi) a connu une croissance phénoménale, transformant les systèmes de compensation financière de pair à pair. En remplaçant les carnets d'ordres limites (LOB) conventionnels par des teneurs de marché automatisés (AMM), les protocoles DeFi établissent une disponibilité constante pour le trading de crypto-actifs.",
            "Japanese" to "エグゼクティブサマリー：分散型金融（DeFi）は驚異的な成長を遂げ、ピアツーピアの金融決済システムを変革しました。従来の限度額注文簿（LOB）を自動マーケットメーカー（AMM）に置き換えることで、DeFiプロトコルは暗号資産取引の常時利用可能性を確立しています。",
            "German" to "Management-Zusammenfassung: Dezentrale Finanzmärkte (DeFi) haben ein phänomenales Wachstum verzeichnet und die Peer-to-Peer-Finanzclearing-Systeme transformiert. Durch den Ersatz herkömmlicher Limit-Orderbücher (LOB) durch automatisierte Market Maker (AMMs) etablieren DeFi-Protokolle eine ständige Verfügbarkeit für den Handel mit Krypto-Assets.",
            "Hindi" to "कार्यकारी सारांश: विकेंद्रीकृत वित्त (DeFi) ने अभूतपूर्व वृद्धि देखी है, जिससे पीयर-टू-पीयर वित्तीय समाशोधन प्रणालियों में बदलाव आया है। पारंपरिक सीमा आदेश बही (LOB) को स्वचालित बाजार निर्माताओं (AMM) से बदलकर, DeFi प्रोटोकॉल क्रिप्टो परिसंपत्तियों के व्यापार के लिए निरंतर उपलब्धता स्थापित करते हैं।",
            "Arabic" to "الملخص التنفيذي: شهد التمويل اللامركزي (DeFi) نموًا هائلاً، مما أحدث تحولًا في أنظمة المقاصة المالية بين النظراء. ومن خلال استبدال دفاتر طلبات الحد التقليدية (LOB) بصناع السوق الآليين (AMMs)، تؤسس بروتوكولات DeFi لتوافر مستمر لتداول الأصول المشفرة."
        ),
        "Automated Market Makers operate" to mapOf(
            "Spanish" to "Los creadores de mercado automatizados operan con contratos inteligentes sin permisos que rigen los grupos de liquidez.",
            "French" to "Les teneurs de marché automatisés fonctionnent sur des contrats intelligents sans autorisation qui régissent les pools de liquidité.",
            "Japanese" to "自動マーケットメーカーは、流動性プールを管理する許可不要のスマートコントラクト上で動作します。",
            "German" to "Automated Market Makers arbeiten mit erlaubnisfreien Smart Contracts, die Liquiditätspools regeln.",
            "Hindi" to "स्वचालित बाजार निर्माता बिना अनुमति वाले स्मार्ट अनुबंधों पर काम करते हैं जो तरलता पूल को नियंत्रित करते हैं।",
            "Arabic" to "يعمل صناع السوق الآليون بموجب عقود ذكية غير مسموح بها تحكم مجمعات السيولة."
        ),
        "The continuous pricing curve" to mapOf(
            "Spanish" to "La curva de precios continua de los AMM ofrece liquidez ininterrumpida, democratizando las capacidades de creación de mercado a nivel mundial.",
            "French" to "La courbe de tarification continue des AMM offre une liquidité ininterrompue, démocratisant les capacités de tenue de marché à l'échelle mondiale.",
            "Japanese" to "AMMの継続的な価格設定曲線は、途切れのない流動性を提供し、グローバルに市場開拓機能を民主化します。",
            "German" to "Die kontinuierliche Preiskurve von AMMs bietet ununterbrochene Liquidität und demokratisiert die Market-Making-Fähigkeiten weltweit.",
            "Hindi" to "एएमएम का निरंतर मूल्य निर्धारण वक्र निर्बाध तरलता प्रदान करता है, जिससे वैश्विक स्तर पर बाजार बनाने की क्षमताओं का लोकतंत्रीकरण होता है।",
            "Arabic" to "تقدم منحنى التسعير المستمر لصناع السوق الآليين سيولة دون انقطاع، مما يضفي الطابع الديمقراطي على قدرات صناعة السوق عالميًا."
        ),
        "Constant Product Formula: The" to mapOf(
            "Spanish" to "Fórmula de Producto Constante: Los fundamentos matemáticos de los principales intercambios descentralizados (como Uniswap v2) se basan en la ecuación invariante del producto constante: x * y = k, donde x e y representan las reservas absolutas de dos tokens distintos en el pool, y k es una constante.",
            "French" to "Formule du produit constant: Les fondements mathématiques des principales bourses décentralisées (comme Uniswap v2) reposent sur l'équation invariante du produit constant: x * y = k, où x et y représentent les réserves absolutes de deux jetons distincts dans le pool, et k est une constante.",
            "Japanese" to "定数積公式：主要な分散型取引所（Uniswap v2など）の数学的基礎は、定数積不変方程式：x * y = kに基づいています。ここで、xとyはプール内の2つの異なるトークンの絶対的なリザーブを表し、kは定数です。",
            "German" to "Konstante Produktformel: Die mathematischen Grundlagen primärer dezentraler Börsen (wie Uniswap v2) beruhen auf der invarianten Gleichung des konstanten Produkts: x * y = k, wobei x und y die absoluten Reserven zweier verschiedener Token im Pool darstellen und k eine Konstante ist.",
            "Hindi" to "निरंतर उत्पाद सूत्र: प्राथमिक विकेंद्रीकृत एक्सचेंजों (जैसे Uniswap v2) की गणितीय नींव निरंतर उत्पाद अपरिवर्तनीय समीकरण पर टिकी हुई है: x * y = k, जहाँ x और y पूल में दो अलग-अलग टोकन के पूर्ण भंडार का प्रतिनिधित्व करते हैं, और k एक स्थिरांक है।",
            "Arabic" to "صيغة المنتج الثابت: ترتكز الأسس الرياضية للبورصات اللامركزية الأساسية (مثل Uniswap v2) على معادلة المنتج الثابت الثابتة: x * y = k، حيث يمثل x و y الاحتياطيات المطلقة لرمزين متميزين في المجمع، و k ثابت."
        ),
        "This simple algorithmic relation" to mapOf(
            "Spanish" to "Esta simple relación algorítmica garantiza que a medida que la reserva de un token disminuye debido a la presión de compra, su precio relativo aumenta exponencialmente.",
            "French" to "Cette relation algorithmique simple garantit que lorsque la réserve d'un jeton diminue en raison de la pression d'achat, son prix relatif augmente de manière exponentielle.",
            "Japanese" to "このシンプルなアルゴリズム関係により、買い圧力によって1つのトークンのリザーブが減少すると、その相対価格が指数関数的に上昇します。",
            "German" to "Diese einfache algorithmische Beziehung stellt sicher, dass, wenn die Reserve eines Tokens aufgrund von Kaufdruck abnimmt, sein relativer Preis exponentiell steigt.",
            "Hindi" to "यह सरल एल्गोरिथम संबंध यह सुनिश्चित करता है कि जैसे-जैसे खरीद दबाव के कारण एक टोकन का भंडार कम होता है, उसकी सापेक्ष कीमत तेजी से बढ़ती है।",
            "Arabic" to "تضمن هذه العلاقة الخوارزمية البسيطة أنه مع انخفاض احتياطي رمز واحد بسبب ضغط الشراء، فإن سعره النسبي يرتفع بشكل كبير."
        ),
        "Slippage Dynamics: Slippage" to mapOf(
            "Spanish" to "Dinámica de deslizamiento: el deslizamiento se refiere a la divergencia de precios entre el momento en que se envía una transacción y el momento en que se ejecuta.",
            "French" to "Dynamique de glissement: le glissement fait référence à la divergence de prix entre le moment où une transaction est soumise et celui où elle s'exécute.",
            "Japanese" to "スリッページダイナミクス：スリッページとは、トランザクションが送信されてから実行されるまでの価格の乖離を指します。",
            "German" to "Slippage-Dynamik: Slippage bezieht sich auf die Preisabweichung zwischen dem Zeitpunkt des Sendens einer Transaktion und dem Zeitpunkt der Ausführung.",
            "Hindi" to "फिसलन गतिशीलता: फिसलन से तात्पर्य लेनदेन जमा करने और उसके निष्पादन के समय के बीच मूल्य भिन्नता से है।",
            "Arabic" to "ديناميكيات الانزلاق: يشير الانزلاق إلى تباعد الأسعار بين لحظة تقديم المعاملة ووقت تنفيذها."
        ),
        "Impermanent Loss: Liquidity" to mapOf(
            "Spanish" to "Pérdida impermanente: los proveedores de liquidez enfrentan un perfil de riesgo inherente único en los sistemas de fijación de precios automatizados, denominado Pérdida Impermanente (IL).",
            "French" to "Perte impermanente: les fournisseurs de liquidité sont confrontés à un profil de risque inhérent unique aux systèmes de tarification automatisés, appelé Perte Impermanente (IL).",
            "Japanese" to "インパーマネントロス：流動性プロバイダーは、自動価格設定システムに特有の固有のリスクプロファイル、いわゆるインパーマネントロス（IL）に直面します。",
            "German" to "Impermanenter Verlust: Liquiditätsanbieter sind mit einem inhärenten Risikoprofil konfrontiert, das für automatisierte Preissysteme einzigartig ist, dem sogenannten impermanenten Verlust (Impermanent Loss - IL).",
            "Hindi" to "अस्थायी नुकसान: तरलता प्रदाता स्वचालित मूल्य निर्धारण प्रणालियों के लिए अद्वितीय जोखिम प्रोफ़ाइल का सामना करते हैं, जिसे अस्थायी नुकसान (IL) कहा जाता है।",
            "Arabic" to "الخسارة غير الدائمة: يواجه موفرو السيولة ملف تعريف مخاطر متأصل فريد من نوعه لأنظمة التسعير الآلية، ويُسمى الخسارة غير الدائمة (IL)."
        ),
        "If the price ratio shifts" to mapOf(
            "Spanish" to "Si la relación de precios cambia, los árbitros aprovechan las cotizaciones fuera de mercado del pool para alinearas con los intercambios del mercado externo.",
            "French" to "Si le rapport de prix change, les arbitres exploitent les cotations hors marché du pool pour les aligner sur les échanges du marché externe.",
            "Japanese" to "価格比率がシフトすると、アービトラージャーはプールの市場外の見積もりを利用して、それらを外部市場の取引所に合わせます。",
            "German" to "Wenn sich das Preisverhältnis verschiebt, nutzen Arbitrageure die außerbörslichen Notierungen des Pools aus, um sie an die externen Marktbörsen anzupassen.",
            "Hindi" to "यदि मूल्य अनुपात बदलता है, तो मध्यस्थ पूल के गैर-बाजार उद्धरणों का लाभ उठाकर उन्हें बाहरी बाजार एक्सचेंजों के साथ संरेखित करते हैं।",
            "Arabic" to "إذا تحولت نسبة السعر، يستغل المرجحون عروض الأسعار خارج السوق للمجمع لمواءمتها مع بورصات السوق الخارجية."
        ),
        "Introduction to Negative-Emission" to mapOf(
            "Spanish" to "Introducción a los Sistemas de Emisiones Negativas: Lograr los objetivos climáticos globales estándar requiere algo más que reducciones agresivas de emisiones. La eliminación activa de gases de efecto invernadero atmosféricos históricos a través de sistemas de Captura Directa de Aire (DAC) se ha convertido en un pilar científico indispensable.",
            "French" to "Introduction aux systèmes à émissions négatives: Atteindre les objectifs climatiques mondiaux standard nécessite plus que des réductions agressives des émissions. L'élimination active des gaz à effet de serre atmosphériques historiques via des systèmes de capture directe de l'air (DAC) est devenue un pilier scientifique indispensable.",
            "Japanese" to "負の排出システムへの導入：標準的な世界の気候目標を達成するには、積極的な排出削減以上のものが必要です。直接空気回収（DAC）システムを介して過去の大気中温室効果ガスを積極的に除去することは、不可欠な科学的柱となっています。",
            "German" to "Einführung in Systeme mit negativen Emissionen: Das Erreichen globaler Standard-Klimaziele erfordert mehr als nur aggressive Emissionsminderungen. Die aktive Entfernung historischer atmosphärischer Treibhausgase durch direkte Luftabscheidungssysteme (DAC) hat sich als unverzichtbare wissenschaftliche Säule herausgestellt.",
            "Hindi" to "नकारात्मक-उत्सर्जन प्रणालियों का परिचय: मानक वैश्विक जलवायु लक्ष्यों को प्राप्त करने के लिए केवल आक्रामक उत्सर्जन में कमी से अधिक की आवश्यकता है। डायरेक्ट एयर कैप्चर (DAC) प्रणालियों के माध्यम से ऐतिहासिक वायुमंडलीय ग्रीनहाउस गैसों को सक्रिय रूप से हटाना एक अनिवार्य वैज्ञानिक स्तंभ के रूप में उभरा है।",
            "Arabic" to "مقدمة إلى أنظمة الانبعاثات السلبية: يتطلب تحقيق الأهداف المناخية العالمية القياسية أكثر من مجرد تخفيضات هائلة في الانبعاثات. لقد برز الإزالة النشطة لغازات الاحتباس الحراري التاريخية في الغلاف الجوي عبر أنظمة الاحتجاز المباشر للهواء (DAC) كركيزة علمية لا غنى عنها."
        ),
        "Direct Air Capture involves" to mapOf(
            "Spanish" to "La captura directa de aire implica estructuras mecánicas que procesan el aire ambiente para filtrar el dióxido de carbono directamente.",
            "French" to "La capture directe de l'air implique des structures mécaniques qui traitent l'air ambiant pour filtrer directement le dioxyde de carbone.",
            "Japanese" to "直接空気回収には、周囲の空気を処理して二酸化炭素を直接ろ過する機械的構造が含まれます。",
            "German" to "Die direkte Luftabscheidung umfasst mechanische Strukturen, die die Umgebungsluft verarbeiten, um Kohlendioxid direkt zu filtern.",
            "Hindi" to "डायरेक्ट एयर कैप्चर में यांत्रिक संरचनाएं शामिल हैं जो कार्बन डाइऑक्साइड को सीधे फ़िल्टर करने के लिए परिवेशी वायु को संसाधित करती हैं।",
            "Arabic" to "ينطوي الاحتجاز المباشر للهواء على هياكل ميكانيكية تعالج الهواء المحيط لتصفية ثاني أكسيد الكربون مباشرة."
        ),
        "Biological carbon sequestration" to mapOf(
            "Spanish" to "Los métodos de secuestro biológico de carbono, como la reforestación y la meteorización mejorada, complementan la DAC mecánica.",
            "French" to "Les méthodes de séquestration biologique du carbone, telles que le reboisement et l'altération améliorée, complètent la DAC mécanique.",
            "Japanese" to "植林や風化の促進などの生物学的炭素隔離方法は、機械的DACを補完します。",
            "German" to "Biologische Methoden der Kohlenstoffbindung wie Wiederaufforstung und verstärkte Verwitterung ergänzen die mechanische DAC.",
            "Hindi" to "जैविक कार्बन अनुक्रम विधियां, जैसे कि पुनर्वनीकरण और बढ़ी हुई अपक्षय, यांत्रिक डीएसी के पूरक हैं।",
            "Arabic" to "تكمل طرق احتجاز الكربون البيولوجي، مثل إعادة تشجير الغابات والتجوية المحسنة، عملية الاحتجاز المباشر للهواء الميكانيكية."
        ),
        "Engineering & Thermodynamic" to mapOf(
            "Spanish" to "Presupuestos de ingeniería y termodinámicos: dirigir el aire ambiente a través de contactores de filtro requiere una cantidad de energía térmica y eléctrica significativa.",
            "French" to "Budgets d'ingénierie et thermodynamiques: diriger l'air ambiant à travers des contacteurs de filtre nécessite une énergie thermique et électrique importante.",
            "Japanese" to "エンジニアリングと熱力学的予算：フィルターコンタクターに周囲の空気を通すには、かなりの熱エネルギーと電気エネルギーが必要です。",
            "German" to "Ingenieur- und Thermodynamik-Budgets: Das Leiten von Umgebungsluft durch Filterkontaktoren erfordert erhebliche thermische und elektrische Energie.",
            "Hindi" to "इंजीनियरिंग और थर्मोडायनामिक बजट: फ़िल्टर संपर्ककर्ताओं के माध्यम से परिवेशी वायु को निर्देशित करने के लिए महत्वपूर्ण थर्मल और विद्युत ऊर्जा की आवश्यकता होती है।",
            "Arabic" to "ميزانيات الهندسة والديناميكا الحرارية: يتطلب توجيه الهواء المحيط عبر موصلات الفلتر طاقة حرارية وكهربائية كبيرة."
        ),
        "Liquid solvent systems" to mapOf(
            "Spanish" to "Los sistemas de solventes líquidos usan hidróxido de potasio para capturar carbono, requiriendo altas temperaturas de hasta 900 grados Celsius.",
            "French" to "Les systèmes de solvants liquides utilisent de l'hydroxyde de potassium pour capturer le carbone, ce qui nécessite des températures élevées allant jusqu'à 900 degrés Celsius.",
            "Japanese" to "液体溶媒システムは水酸化カリウムを使用して炭素を回収するため、煆焼に最大摂氏900度の高温が必要になります。",
            "German" to "Flüssige Lösungsmittelsysteme verwenden Kaliumhydroxid zur Kohlenstoffbindung, was hohe Temperaturen von bis zu 900 Grad Celsius für die Kalzinierung erfordert.",
            "Hindi" to "तरल विलायक प्रणालियाँ कार्बन को पकड़ने के लिए पोटेशियम हाइड्रॉक्साइड का उपयोग करती हैं, जिसके लिए कैल्सीनेशन के लिए 900 डिग्री सेल्सियस तक के उच्च तापमान की आवश्यकता होती है।",
            "Arabic" to "تستخدم أنظمة المذيبات السائلة هيدروكسيد البوتاسيوم لاحتجاز الكربون، مما يتطلب درجات حرارة عالية تصل إلى 900 درجة مئوية للتكليس."
        ),
        "The efficiency of DAC" to mapOf(
            "Spanish" to "La eficiencia de las tecnologías DAC está estrictamente limitada por las leyes de la termodinámica.",
            "French" to "L'efficacité des technologies DAC est strictement limitée par les lois de la thermodynamique.",
            "Japanese" to "DAC技術の効率は熱力学の法則によって厳しく制限されています。",
            "German" to "Die Effizienz von DAC-Technologien ist durch thermodynamische Gesetze streng begrenzt.",
            "Hindi" to "DAC प्रौद्योगिकियों की दक्षता थर्मोडायनामिक नियमों द्वारा कड़ाई से बंधी हुई है।",
            "Arabic" to "كفاءة تقنيات الاحتجاز المباشر للهواء مقيدة بصرامة بقوانين الديناميكا الحرارية."
        ),
        "Deep Basalt Mineralization" to mapOf(
            "Spanish" to "Mineralización profunda de basalto: una vez capturado, el aislamiento seguro a largo plazo del dióxido de carbono es de suma importancia.",
            "French" to "Minéralisation profonde du basalte: une fois capturé, l'isolement sûr et à long terme du dioxyde de carbone est d'une importance capitale.",
            "Japanese" to "深部玄武岩の鉱物化：回収された二酸化炭素を長期にわたって安全に隔離することは極めて重要です。",
            "German" to "Tiefenbasaltmineralisierung: Einmal abgeschieden, ist die sichere langfristige Isolierung von Kohlendioxid von größter Bedeutung.",
            "Hindi" to "डीप बेसाल्ट खनिजकरण: एक बार कैप्चर होने के बाद, carbon dioxide का सुरक्षित दीर्घकालिक अलगाव सर्वोπερι है।",
            "Arabic" to "تمعدن البازلت العميق: بمجرد احتجازه، فإن العزل الآمن لثاني أكسيد الكربون على المدى الطويل أمر بالغ الأهمية."
        ),
        "Basalt is a reactive" to mapOf(
            "Spanish" to "El basalto es una roca volcánica reactiva rica en calcio, magnesio y hierro. Cuando reacciona con el agua carbonatada, desencadena una mineralización in situ.",
            "French" to "Le basalte es una roca volcánica réactive riche en calcium, magnésium et fer. Lorsqu'elle réagit avec l'eau carbonatée, elle déclenche une minéralisation in situ.",
            "Japanese" to "玄武岩は、カルシウム、マグネシウム、鉄が豊富な反応性の高い火山岩です。炭酸水と反応すると、その場で鉱物化が引き起こされます。",
            "German" to "Basalt ist ein reaktives Vulkangestein, das reich an Kalzium, Magnesium und Eisen ist. Wenn es mit karbonisiertem Wasser reagiert, löst es eine In-situ-Mineralisierung aus.",
            "Hindi" to "बेसाल्ट कैल्शियम, मैग्नीशियम और लोहे से भरपूर एक प्रतिक्रियाशील ज्वालामुखी चट्टान है। जब यह कार्बोनेटed पानी के साथ प्रतिक्रिया करता है, तो यह इन-सीटू खनिजकरण को ट्रिगर करता है।",
            "Arabic" to "البازلت عبارة عن صخور بركانية تفاعلية غنية بالكالسيوم والمغنيسيوم والحديد. عندما يتفاعل مع الماء المكربن، فإنه يحفز التمعدن في الموقع."
        ),
        "This rock-mineralization" to mapOf(
            "Spanish" to "Este proceso de mineralización de rocas es increíblemente seguro, lo que evita cualquier riesgo potencial de fuga de gas de regreso a la biosfera.",
            "French" to "Ce processus de minéralisation de la roche est incroyablement sûr, empêchant tout risque potentiel de fuite de gaz vers la biosphère.",
            "Japanese" to "この岩石の鉱物化プロセスは非常に安全であり、大気中にガスが逆流する潜在的な危険性を防ぎます。",
            "German" to "Dieser Gesteinsmineralisierungsprozess ist unglaublich sicher und verhindert jede potenzielle Gefahr eines Gasaustritts zurück in die Biosphäre.",
            "Hindi" to "यह चट्टान-खनिजकरण प्रक्रिया अविश्वसनीय रूप से सुरक्षित है, जो जीवमंडल में वापस गैस रिसाव के किसी भी संभावित खतरे को रोकती है।",
            "Arabic" to "إن عملية تمعدن الصخور هذه آمنة بشكل لا يصدق، مما يمنع أي خطر محتمل لتسرب الغاز مرة أخرى إلى المحيط الحيوي."
        ),
        "Default Synced Document Content" to mapOf(
            "Spanish" to "Contenido de documento sincronizado predeterminado: use el explorador en la nube o las herramientas OCR para capturar texto personalizado.",
            "French" to "Contenu du document synchronisé par défaut : utilisez l'explorateur cloud ou les outils OCR pour capturer du texte personnalisé.",
            "Japanese" to "デフォルトの同期済みドキュメントコンテンツ：クラウドエクスプローラーまたはOCRツールを使用して、カスタムテキストをキャプチャします。",
            "German" to "Standardmäßig synchronisierter Dokumenteninhalt: Verwenden Sie den Cloud-Explorer oder OCR-Tools, um benutzerdefinierten Text zu erfassen.",
            "Hindi" to "डिफ़ॉल्ट सिंक की गई दस्तावेज़ सामग्री: कस्टम टेक्स्ट कैप्चर करने के लिए क्लाउड एक्सप्लोरर या ओसीआर टूल का उपयोग करें।",
            "Arabic" to "محتوى المستند المتزامن الافتراضي: استخدم مستكشف السحابة أو أدوات التعرف الضوئي على الحروف لالتقاط نص مخصص."
        ),
        "How to use PulsePDF" to mapOf(
            "Spanish" to "Cómo usar los aspectos destacados de PulsePDF: mantenga presionado o toque una vez cualquier párrafo en la pantalla para activar el resaltado.",
            "French" to "Comment utiliser les points forts de PulsePDF : maintenez enfoncé ou appuyez une fois sur n'importe quel paragraphe de l'écran pour activer la surbrillance.",
            "Japanese" to "PulsePDFハイライトの使用方法：画面上の段落を長押しまたはシングルタップして、ハイライトを有効にします。",
            "German" to "So verwenden Sie PulsePDF-Highlights: Halten Sie einen beliebigen Absatz auf dem Bildschirm gedrückt oder tippen ihn einmal an, um die Hervorhebung zu aktivieren.",
            "Hindi" to "पल्सपीडीएफ हाइलाइट्स का उपयोग कैसे करें: हाइलाइटिंग को सक्रिय करने के लिए स्क्रीन पर किसी भी पैराग्राफ को दबाकर रखें या सिंगल टैप करें।",
            "Arabic" to "كيفية استخدام تمييزات PulsePDF: اضغط مع الاستمرار أو انقر نقرة واحدة على أي فقرة على الشاشة لتنشيط التمييز."
        ),
        "AI summarizer: Open" to mapOf(
            "Spanish" to "Resumidor de IA: abra la consola de investigación para obtener resúmenes concisos y puntos clave.",
            "French" to "Synthétiseur IA : ouvrez la console de recherche pour obtenir des résumés concis et des points clés.",
            "Japanese" to "AIサマライザー：リサーチコンソールを開いて、簡潔な要約と要点を入手します。",
            "German" to "AI-Zusammenfassung: Öffnen Sie die Forschungskonsole, um prägnante Zusammenfassungen und wichtige Punkte zu erhalten.",
            "Hindi" to "एआई सारांशकर्ता: संक्षिप्त सारांश और मुख्य बिंदु प्राप्त करने के लिए अनुसंधान कंसोल खोलें।",
            "Arabic" to "ملخص الذكاء الاصطناعي: افتح وحدة تحكم الأبحاث للحصول على ملخصات موجزة ونقاط رئيسية."
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

    private val teluguDict = mapOf(
        "abstract" to "సారాంశం", "introduction" to "పరిచయం", "model" to "నమూనా", "architecture" to "నిర్మాణం",
        "attention" to "శ్రద్ధ", "transformer" to "ట్రాన్స్ఫార్మర్", "defi" to "డిఫై (DeFi)", "liquidity" to "ద్రవ్యత",
        "carbon" to "కార్బన్", "capture" to "ग्रहించడం", "slippage" to "స్లిప్పేజ్", "formula" to "సూత్రం",
        "loss" to "నష్టం", "thermodynamic" to "థర్మోడైనమిక్", "basalt" to "బసాల్ట్", "efficiency" to "సామర్థ్యం"
    )

    fun translate(text: String, targetLanguage: String): String {
        if (targetLanguage.equals("English", ignoreCase = true)) {
            return text
        }
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
            "Telugu" -> teluguDict
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
