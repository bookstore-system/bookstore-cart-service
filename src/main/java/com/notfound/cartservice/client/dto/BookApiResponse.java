package com.notfound.cartservice.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("data")
    public void setData(BookResponse data) {
        this.result = data;
    }

    @JsonProperty("result")
    public void setResult(BookResponse result) {
        this.result = result;
    }

    public BookResponse getBook() {
        return result;
    }
}
