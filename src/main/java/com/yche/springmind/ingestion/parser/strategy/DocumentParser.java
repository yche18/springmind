package com.yche.springmind.ingestion.parser.strategy;

import java.io.InputStream;

public interface DocumentParser {

    boolean supports(String extension);

    String parse(InputStream inputStream);
}
