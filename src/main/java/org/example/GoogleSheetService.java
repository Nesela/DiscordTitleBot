package org.example;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class GoogleSheetService {
    private static final String SPREADSHEET_ID = "1fPcmdfE_5scHzh2mXhdtDGPzlpBybvkibHWNWeRViCY";

    public static Sheets getSheetsService() throws Exception {
        // 레일웨이에서 설정한 환경변수 GOOGLE_CREDENTIALS를 가져옵니다
        String jsonContent = System.getenv("GOOGLE_CREDENTIALS");

        // 문자열을 InputStream으로 변환
        InputStream serviceAccount = new ByteArrayInputStream(jsonContent.getBytes());

        GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount)
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