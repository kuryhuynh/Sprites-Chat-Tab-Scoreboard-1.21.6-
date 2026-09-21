package com.kury.spriteschat;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

public class SerializationTest {

    public static void main(String[] args) {
        new SerializationTest().testSerialization();
    }

    @Test
    void testSerialization() {
        Component cPlayer = Component.object(ObjectContents.playerHead("Notch"));
        System.out.println("cPlayer: " + GsonComponentSerializer.gson().serialize(cPlayer));

        Component cKury = Component.object(ObjectContents.playerHead("Kury"));
        System.out.println("cKury: " + GsonComponentSerializer.gson().serialize(cKury));

        Component cEmptyHead = Component.object(ObjectContents.playerHead().build());
        System.out.println("cEmptyHead: " + GsonComponentSerializer.gson().serialize(cEmptyHead));

        Component cSprite = Component.object(ObjectContents.sprite(Key.key("minecraft", "blocks"), Key.key("minecraft", "block/spawner")));
        System.out.println("cSprite: " + GsonComponentSerializer.gson().serialize(cSprite));

        try {
            Class<?> plainClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Object plain = plainClass.getMethod("plainText").invoke(null);
            java.lang.reflect.Method ser = plainClass.getMethod("serialize", Component.class);
            System.out.println("cPlayer plain: " + ser.invoke(plain, cPlayer));
            System.out.println("cEmptyHead plain: " + ser.invoke(plain, cEmptyHead));
            System.out.println("cSprite plain: " + ser.invoke(plain, cSprite));
        } catch (Throwable t) {
            System.out.println("Plain serializer failed: " + t);
        }
    }
}
