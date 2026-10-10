package polyclinic.queue.util;

public final class Validators {

    private Validators() {

    }

    public static boolean isValidFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return false;

        if (!fullName.matches("^[А-ЯA-Zа-яa-zЁё\\s\\-']+")) return false;

        String[] words = fullName.trim().split("\\s+");
        if (words.length < 2 || words.length > 3) return false;

        for (String word : words) {
            if (!word.matches("^[А-ЯA-ZЁ][а-яa-zA-Zё]*([-'][А-ЯA-Zа-яa-zA-Zё]+)*$")) return false;
        }

        return true;
    }

    public static boolean isValidOffice(String text) {
        if (text == null) return false;
        return text.trim().matches("^\\d+(-?[а-яА-Яa-zA-Z])?$");
    }
}