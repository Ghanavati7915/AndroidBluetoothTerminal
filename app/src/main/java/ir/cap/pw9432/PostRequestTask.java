package ir.cap.pw9432;

import android.os.AsyncTask;
import android.util.Log;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONObject;

import java.io.IOException;
public class PostRequestTask extends AsyncTask<Void, Void, String> {

    private JSONObject requestBody;

    public PostRequestTask(JSONObject requestBody) {
        this.requestBody = requestBody;

        Log.w("Ghanavati RB" , this.requestBody.toString());

    }

    @Override
    protected String doInBackground(Void... voids) {
        try {
            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, String.valueOf(this.requestBody));
            Request request = new Request.Builder()
                    .url("https://b.pingcloud.ir/api/v1/Aware/Insert")
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            Response response = client.newCall(request).execute();
            Log.w("Ghanavati resp" , response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }
}

