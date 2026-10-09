package com.example.dharani.publicdata.model;

import java.util.Map;

/** Label -> value, in the same order as the portal shows them. */
public record LandDetailsResult(
        Map<String, String> pattadarDetails,
        Map<String, String> landDetails) {

    public boolean isEmpty() {
        return pattadarDetails.isEmpty() && landDetails.isEmpty();
    }
}