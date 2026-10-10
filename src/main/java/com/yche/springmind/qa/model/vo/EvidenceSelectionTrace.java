package com.yche.springmind.qa.model.vo;

public record EvidenceSelectionTrace(String rerankStatus, int inputCandidates, int selectedCount,
                                     int inputTokens, int selectedTokens, int maxTokens) { }
