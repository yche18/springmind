package com.yche.springmind.qa.controller;

import com.yche.springmind.qa.service.EvidenceSourceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/qa/evidence")
public class EvidenceSourceController {
    private final EvidenceSourceService sources;

    public EvidenceSourceController(EvidenceSourceService sources) {
        this.sources = sources;
    }

    @GetMapping
    public List<EvidenceSourceService.SourceChunk> read(HttpServletRequest request,
            @RequestParam Long groupId, @RequestParam Long documentId,
            @RequestParam int start, @RequestParam int end) {
        return sources.read(request, groupId, documentId, start, end);
    }
}
