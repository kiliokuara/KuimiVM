package android.content;

public class ContentResolver {
    static ContentResolver instance=new ContentResolver();
    public static ContentResolver getInstance() {
        return instance;
    }
}
