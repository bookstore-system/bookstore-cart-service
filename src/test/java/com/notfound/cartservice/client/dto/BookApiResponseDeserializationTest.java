package com.notfound.cartservice.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BookApiResponseDeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesDataWrapperAndImageUrls() throws Exception {
        String json = """
                {
                  "code": 200,
                  "message": "Success",
                  "data": {
                    "id": "f8463ef9-0e59-477f-b757-6fd4af974b1e",
                    "title": "vat",
                    "price": 100000,
                    "stockQuantity": 100,
                    "imageUrls": [
                      "http://res.cloudinary.com/demo/book.webp"
                    ]
                  }
                }
                """;

        BookApiResponse api = mapper.readValue(json, BookApiResponse.class);

        assertNotNull(api.getResult());
        assertEquals("vat", api.getResult().getTitle());
        assertEquals(
                "http://res.cloudinary.com/demo/book.webp",
                api.getResult().resolveImageUrl()
        );
    }
}
