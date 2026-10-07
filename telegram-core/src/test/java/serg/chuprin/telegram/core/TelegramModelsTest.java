package serg.chuprin.telegram.core;
public final class TelegramModelsTest {
    public static void main(String[] args) {
        TelegramChat chat=new TelegramChat(1L,"Test",false,false);
        if(chat.id!=1L) throw new AssertionError();
        TelegramMedia media=new TelegramMedia(TelegramMedia.Type.PHOTO,"/tmp/a.jpg","image/jpeg");
        if(media.type!=TelegramMedia.Type.PHOTO) throw new AssertionError();
        System.out.println("Telegram core model smoke test: PASS");
    }
}
