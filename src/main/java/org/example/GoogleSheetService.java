package org.example;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.util.Collections;
import java.util.List;

public class GoogleSheetService { // 클래스 이름을 파일명과 똑같이 유지하세요
    private static final String SPREADSHEET_ID = "1fPcmdfE_5scHzh2mXhdtDGPzlpBybvkibHWNWeRViCY";
    private static final String JSON_PATH = "discordbot-497307-49605bfd20e5.json";

    public static Sheets getSheetsService() throws Exception {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(JSON_PATH))
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
        return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), new HttpCredentialsAdapter(credentials))
                .setApplicationName("DiscordBot").build();
    }

    public static List<List<Object>> getValues(String range) throws Exception {
        return getSheetsService().spreadsheets().values().get(SPREADSHEET_ID, range).execute().getValues();
    }

    public static void updateValues(String range, List<List<Object>> values) throws Exception {
        ValueRange body = new ValueRange().setValues(values);
        getSheetsService().spreadsheets().values().update(SPREADSHEET_ID, range, body).setValueInputOption("RAW").execute();
    }
}