package org.openjcontroltower.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test void normalisesAndValidatesPolicySettings() {
        assertEquals(new Settings.Policy(true,"http://opa:8181","openjcontroltower/decision",false),
            new Settings.Policy(true," http://opa:8181/ ","/openjcontroltower/decision/",false).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Policy(true,"http://opa:8181",null,false).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Policy(true,"http://opa:8181","a/../b",false).validated());
        assertThrows(IllegalArgumentException.class,()->new Settings.Policy(true,null,"a/b",false).validated());
    }
    @Test void acceptsOnlyPlainHttpUrls() {
        assertEquals("https://h.example/p",Settings.httpUrl("https://h.example/p//","URL"));
        assertEquals("URL is not a valid URL",assertThrows(IllegalArgumentException.class,()->Settings.httpUrl("http://h x","URL")).getMessage());
        assertThrows(IllegalArgumentException.class,()->Settings.httpUrl("http:///path","URL"));
        assertThrows(IllegalArgumentException.class,()->Settings.httpUrl("mailto:a@b","URL"));
        for (String bad:new String[]{"https://u:p@h.example","https://h.example?q=1","https://h.example#f"})
            assertThrows(IllegalArgumentException.class,()->Settings.httpUrl(bad,"URL"),bad);
    }
}
