package com.kgqa.controller;

import com.kgqa.config.TokenAuthInterceptor;
import com.kgqa.service.DataImportService;
import com.kgqa.service.TextBookImportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataImportControllerTest {
    @Test
    void regularUserCannotPublishSharedDocuments() {
        var imports = mock(DataImportService.class);
        var books = mock(TextBookImportService.class);
        var controller = new DataImportController(imports, books);
        var request = new MockHttpServletRequest();
        request.setAttribute(TokenAuthInterceptor.ROLE_ATTRIBUTE, "USER");

        var csvError = assertThrows(ResponseStatusException.class,
                () -> controller.importFromCSV("file.csv", "title", "", request));
        var textbookError = assertThrows(ResponseStatusException.class,
                () -> controller.importTextbook("books", "title", request));
        assertEquals(HttpStatus.FORBIDDEN, csvError.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, textbookError.getStatusCode());
        verifyNoInteractions(imports, books);
    }

    @Test
    void adminCanImportSharedCsv() {
        var imports = mock(DataImportService.class);
        var books = mock(TextBookImportService.class);
        var controller = new DataImportController(imports, books);
        var request = new MockHttpServletRequest();
        request.setAttribute(TokenAuthInterceptor.ROLE_ATTRIBUTE, "ADMIN");
        when(imports.importFromCSV("file.csv", "title", "")).thenReturn(2);

        assertEquals(true, controller.importFromCSV("file.csv", "title", "", request).get("success"));
        verify(imports).importFromCSV("file.csv", "title", "");
    }
}
