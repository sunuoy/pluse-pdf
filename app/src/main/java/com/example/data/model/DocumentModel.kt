package com.example.data.model

data class PdfPageModel(
    val pageNumber: Int,
    val paragraphs: List<String>
)

object PdfDocumentLines {
    fun getPagesForDocument(sourcePath: String): List<PdfPageModel> {
        return when (sourcePath) {
            "attention_all_you_need.pdf" -> listOf(
                PdfPageModel(
                    pageNumber = 1,
                    paragraphs = listOf(
                        "Abstract: The dominant sequence transduction models are based on complex recurrent or convolutional neural networks that include an encoder and a decoder. The best performing models also connect the encoder and decoder through an attention mechanism. We propose a new simple network architecture, the Transformer, based solely on attention mechanisms, dispensing with recurrence and convolutions entirely.",
                        "Introduction: Recurrent neural networks (RNNs), long short-term memory (LSTM) and gated recurrent neural networks in particular, have been firmly established as state of the art approaches in sequence modeling and transduction problems such as language modeling and machine translation.",
                        "Recurrent models factor computation along the symbol positions of the input and output sequences. Aligning the positions to steps in computation time, they generate a sequence of hidden states, as a function of the previous hidden state and the input at the current position.",
                        "This inherent sequential nature precludes parallelization within training examples, which becomes critical at longer sequence lengths, as memory constraints limit batching across examples. Attention mechanisms have become an integral part of compelling sequence modeling in various tasks, allowing modeling of dependencies without regard to their distance."
                    )
                ),
                PdfPageModel(
                    pageNumber = 2,
                    paragraphs = listOf(
                        "Model Architecture: Most competitive neural sequence transduction models have an encoder-decoder structure. Here, the encoder maps an input sequence of symbol representations to a sequence of continuous representations. Given this, the decoder then generates an output sequence of symbols one element at a time.",
                        "The Transformer follows this overall architecture using stacked self-attention and point-wise, fully connected layers for both the encoder and decoder, shown in the left and right halves of Figure 1.",
                        "An attention function can be described as mapping a query and a set of key-value pairs to an output, where the query, keys, values, and output are all vectors. The output is computed as a weighted sum of the values, where the weight assigned to each value is computed by a compatibility function of the query with the corresponding key.",
                        "Multi-Head Attention: Instead of performing a single attention function with d-dimensional queries, keys and values, we found it beneficial to linearly project the queries, keys and values h times with different, learned linear projections. On each of these projected versions we then perform the attention function in parallel."
                    )
                ),
                PdfPageModel(
                    pageNumber = 3,
                    paragraphs = listOf(
                        "Positional Encoding: Since our model contains no recurrence and no convolution, in order for the model to make use of the order of the sequence, we must inject some information about the relative or absolute position of the tokens in the sequence.",
                        "To this end, we add \"positional encodings\" to the input embeddings at the bottoms of the encoder and decoder stacks. The positional encodings have the same dimension as the embeddings, so that the two can be summed.",
                        "Why Self-Attention: In this section we compare various aspects of self-attention layers to the recurrent and convolutional layers commonly used for mapping one variable-length sequence of symbol representations to another sequence of equal length.",
                        "The first is the total computational complexity per layer. The second is the amount of computation that can be parallelized, as measured by the minimum number of sequential operations required."
                    )
                ),
                PdfPageModel(
                    pageNumber = 4,
                    paragraphs = listOf(
                        "Training & Results: This section describes the training regime for our models. We trained on the standard WMT 2014 English-to-German dataset consisting of about 4.5 million sentence pairs.",
                        "Sentences were encoded using byte-pair encoding, which has a shared source-target vocabulary of about 37000 tokens. For English-to-French, we used the significantly larger WMT 2014 English-to-French dataset.",
                        "On the English-to-German translation task, our big model outperforms the best previously reported models (including ensembles) by more than 2.0 BLEU, establishing a new state-of-the-art BLEU score of 28.4.",
                        "On the English-to-French translation task, our big model establishes a new state-of-the-art BLEU score of 41.8, outperforming all of the previously published single models at a fraction of the training cost."
                    )
                )
            )
            "defi_liquidity_report.pdf" -> listOf(
                PdfPageModel(
                    pageNumber = 1,
                    paragraphs = listOf(
                        "Executive Summary: Decentralized Finance (DeFi) has witnessed phenomenal growth, transforming peer-to-peer financial clearing systems. By replacing conventional limit order books (LOB) with Automated Market Makers (AMMs), DeFi protocols establish constant availability for trading crypto assets.",
                        "Automated Market Makers operate on permissionless smart contracts that govern liquidity pools. Liquidity providers (LPs) deposit pairs of corresponding tokens, allowing autonomous traders to swap assets directly against the smart contract's pooled reserves.",
                        "The continuous pricing curve of AMMs offers uninterrupted liquidity, democratizing market-making capabilities globally. Any participant can act as a market maker by simply staking assets into a designated pool, earning a proportional share of transaction fees."
                    )
                ),
                PdfPageModel(
                    pageNumber = 2,
                    paragraphs = listOf(
                        "Constant Product Formula: The mathematical underpinnings of primary decentralized exchanges (like Uniswap v2) rest on the constant product invariant equation: x * y = k, where x and y represent the absolute reserves of two distinct tokens in the pool, and k is a constant.",
                        "This simple algorithmic relation ensures that as the reserve of one token decreases due to buy pressure, its relative price rises exponentially relative to the opposing token, ensuring the pool never depletes its aggregate asset tokens.",
                        "Slippage Dynamics: Slippage refers to the price divergence between the moment a transaction is submitted and when it executes. This deviation increases with trade size relative to pool depth (relative k-value), making deep liquidity pools highly preferred."
                    )
                ),
                PdfPageModel(
                    pageNumber = 3,
                    paragraphs = listOf(
                        "Impermanent Loss: Liquidity providers face an inherent risk profile unique to automated pricing systems, termed Impermanent Loss (IL). This loss occurs when the market price ratio of the staked tokens diverges from the ratio when they were deposited.",
                        "If the price ratio shifts, arbitrageurs exploit the pool's off-market quotes to align them with aggregate external market exchanges, leaving LPs with a greater quantity of the devalued token and less of the appreciated token compared to simply holding them.",
                        "Mitigating Impermanent Loss: Modern DeFi structures introduce concentrated liquidity range boundaries (Uniswap v3) or dynamic pricing algorithms (Curve Finance) to optimize capital efficiency and minimize IL exposure dramatically."
                    )
                )
            )
            "climate_carbon_capture.pdf" -> listOf(
                PdfPageModel(
                    pageNumber = 1,
                    paragraphs = listOf(
                        "Introduction to Negative-Emission Systems: Achieving standard global climate targets requires more than aggressive emission reductions. Active removal of historic atmospheric greenhouse gases via Direct Air Capture (DAC) systems has emerged as an indispensable scientific pillar.",
                        "Direct Air Capture involves mechanical structures that process ambient air to filter carbon dioxide directly. The extracted carbon dioxide is then compressed, transported, and permanently sequestered or recycled into industrial applications.",
                        "Biological carbon sequestration methods, such as reforestation and enhanced weathering, complement mechanical DAC. However, biological capture remains highly vulnerable to natural disturbances like forest fires and human policy swings."
                    )
                ),
                PdfPageModel(
                    pageNumber = 2,
                    paragraphs = listOf(
                        "Engineering & Thermodynamic Budgets: Directing ambient air through filter contactors requires significant thermal and electrical energy. Carbon dioxide represents only 420 parts per million (ppm) of atmospheric air, requiring colossal processing volumes.",
                        "Liquid solvent systems use potassium hydroxide to capture carbon, requiring high temperatures of up to 900 degrees Celsius for calcination. Solid sorbent systems utilize chemical filters that release CO2 at lower temperatures (around 100 degrees Celsius).",
                        "The efficiency of DAC technologies is strictly bounded by thermodynamic laws. Currently, capturing one ton of carbon dioxide requires up to 1500 to 2500 kilowatt-hours of electrical and heat energy."
                    )
                ),
                PdfPageModel(
                    pageNumber = 3,
                    paragraphs = listOf(
                        "Deep Basalt Mineralization: Once captured, safe long-term isolation of carbon dioxide is paramount. The Carbon-Fix process dissolves carbon dioxide in water and injects it thousands of meters deep into basaltic bedrock formations.",
                        "Basalt is a reactive volcanic rock rich in calcium, magnesium, and iron. When acidic carbonated water reacts with basalts, it triggers in-situ mineral carbonation, turning the gas into permanent stone (petrification) within less than two years.",
                        "This rock-mineralization process is incredibly secure, preventing any potential hazard of gas leakage back into the biosphere. Solid carbonate stones can withstand catastrophic seismic tremors and volcanic events safely."
                    )
                )
            )
            else -> {
                if (sourcePath.startsWith("custom_local_")) {
                    val titleClean = sourcePath.substringAfter("custom_local_").replace("_", " ").removeSuffix(".pdf")
                    listOf(
                        PdfPageModel(
                            pageNumber = 1,
                            paragraphs = listOf(
                                "Executive Summary: This parsed publication on '$titleClean' details our comprehensive experimental and theoretical findings. In this chapter, we outline the fundamental research objectives, the historical background of the study, and the core structural frameworks utilized in our analysis.",
                                "Introduction & Frameworks: Modern research approaches in this domain emphasize highly scalable, distributed paradigms. Understanding the micro-architecture and performance trade-offs is critical to optimizing overall efficiency. We investigate the relationship between system inputs and final output metrics.",
                                "Historical Precedents: Historically, systems of this nature suffered from high latency and significant resource overheads. By introducing automated pipeline scheduling and adaptive feedback loops, our model achieves a substantial improvement in resource utilization while maintaining deterministic safety guarantees."
                            )
                        ),
                        PdfPageModel(
                            pageNumber = 2,
                            paragraphs = listOf(
                                "Methodology & Analytical Models: Section 2 covers the rigorous mathematical model of our '$titleClean' implementation. We describe the constant-state invariants and how transactions or data transformations are processed safely across concurrent nodes.",
                                "Dynamic Performance Profiles: Under peak load, the system shows highly linear scalability. We compare our results with established baselines, showing a substantial improvement in throughput and reduced execution divergence.",
                                "Limiting Factors and Bounds: While highly optimized, the operational envelope is strictly bounded by hardware memory bandwidth and network packet serialization latency. We discuss these mitigation strategies in detail."
                            )
                        ),
                        PdfPageModel(
                            pageNumber = 3,
                            paragraphs = listOf(
                                "Conclusion & Future Trajectories: In summary, this '$titleClean' framework represents a major milestone in academic and industrial applications. Our permanent stone-like secure storage prevents data leakage and ensures cross-device consistency.",
                                "Avenue of Future Work: Future investigations will focus on integrating edge-level AI summaries, extending the localized translation matrices, and optimizing real-time collaborative highlighting workflows across cloud networks."
                            )
                        )
                    )
                } else {
                    listOf(
                        PdfPageModel(
                            pageNumber = 1,
                            paragraphs = listOf(
                                "Default Synced Document Content: Use the cloud explorer or OCR tools to capture custom text. All highlights, translations, and annotations on synced texts are updated across devices in real-time.",
                                "How to use PulsePDF highlights: Hold or single tap any paragraph on screen to activate highlighting. Choose yellow, green, teal, or violet styles to organize your research seamlessly.",
                                "AI summarizer: Open the research console to get concise summaries and key bullet takeaways for faster academic research workflows."
                            )
                        )
                    )
                }
            }
        }
    }
}
