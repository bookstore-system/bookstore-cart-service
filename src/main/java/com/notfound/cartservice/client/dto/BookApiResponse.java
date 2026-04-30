package com.notfound.cartservice.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookApiResponse {
    private int code;
    private String message;

    @JsonAlias({"result", "data"})
    private BookResponse result;
}
