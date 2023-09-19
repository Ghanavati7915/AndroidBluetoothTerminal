package de.kai_morich.simple_bluetooth_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;


public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {


    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;


    //region Layers
    private ConstraintLayout layout_display;
    private ConstraintLayout layout_loading;
    private CardView layout_log;

    private CardView card_vin;
    private CardView card_sats;
    private CardView card_antenna;

    private TextView lbl_loading;
    private TextView lbl_value_imei;
    private TextView lbl_value_firmware;
    private TextView lbl_value_serial;
    private TextView lbl_value_model;
    private TextView lbl_value_simcard;
    private TextView lbl_value_operator;
    private TextView lbl_value_adx;
    private TextView lbl_value_snd;
    private TextView lbl_value_sve;
    private TextView lbl_value_mtr;
    private TextView lbl_value_deg;
    private TextView lbl_value_spd;
    private TextView lbl_value_ime;
    private TextView lbl_value_upg;
    private TextView lbl_value_set;
    private TextView lbl_value_vin;
    private TextView lbl_value_acc;
    private TextView lbl_value_rtc;
    private TextView lbl_value_ourMac;
    private TextView lbl_value_hostMac;
    private TextView lbl_value_records;
    private TextView lbl_value_head;
    private TextView lbl_value_tail;
    private TextView lbl_value_btmacsno;
    private TextView lbl_value_gps_antenna;
    private TextView lbl_value_gps_satsno;
    private TextView lbl_value_gps_pdop;
    private TextView lbl_value_gps_time;
    private TextView lbl_value_gps_altitude;
    private TextView lbl_value_gps_latitude;
    private TextView lbl_value_gps_longitude;
    //endregion

    //region Variables
    int ParamsCounter = 0;
    boolean CallGPS = false;
    boolean CallPINGCloud = false;
    String IMEI = "";
    String GPS = "";
    String Model = "";
    String TelNo = "";
    String vin = "";
    String Operator = "";
    String Firmware = "";
    String ADX = "";
    String SND = "";
    String SVE = "";
    String MTR = "";
    String DEG = "";
    String SPD = "";
    String IME = "";
    String UPG = "";
    String SET = "";
    String ACC = "";
    String RTC = "";
    String ourMac = "";
    String hostMac = "";
    String Records = "";
    String Head = "";
    String Tail = "";
    String BtMacsNo = "";
    String SatsNo = "";
    String PDOP = "";
    String Latitude = "";
    String Longitude = "";
    String Altitude = "";
    String Time = "";
    //endregion

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False){
            Toast.makeText(getActivity(), "قطع ارتباط با دستگاه", Toast.LENGTH_SHORT).show();
            disconnect();
        }

        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null) {
            try {
                service.attach(this);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        try {
            service.attach(this);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        layout_loading = view.findViewById(R.id.layout_loading);
        layout_display = view.findViewById(R.id.layout_display);
        layout_log = view.findViewById(R.id.card_log);
        card_vin = view.findViewById(R.id.cardView13);
        card_sats = view.findViewById(R.id.cardView22);
        card_antenna = view.findViewById(R.id.cardView30);

        lbl_loading = view.findViewById(R.id.lbl_loading);

        lbl_value_firmware = view.findViewById(R.id.lbl_value_firmware);
        lbl_value_imei = view.findViewById(R.id.lbl_value_imei);
        lbl_value_serial = view.findViewById(R.id.lbl_value_serial);
        lbl_value_model = view.findViewById(R.id.lbl_value_model);
        lbl_value_simcard = view.findViewById(R.id.lbl_value_simcard);
        lbl_value_operator = view.findViewById(R.id.lbl_value_operator);
        lbl_value_adx = view.findViewById(R.id.lbl_value_adx);
        lbl_value_snd = view.findViewById(R.id.lbl_value_snd);
        lbl_value_sve = view.findViewById(R.id.lbl_value_sve);
        lbl_value_mtr = view.findViewById(R.id.lbl_value_mtr);
        lbl_value_deg = view.findViewById(R.id.lbl_value_deg);
        lbl_value_spd = view.findViewById(R.id.lbl_value_spd);
        lbl_value_ime = view.findViewById(R.id.lbl_value_ime);
        lbl_value_upg = view.findViewById(R.id.lbl_value_upg);
        lbl_value_set = view.findViewById(R.id.lbl_value_set);
        lbl_value_vin = view.findViewById(R.id.lbl_value_vin);
        lbl_value_acc = view.findViewById(R.id.lbl_value_acc);
        lbl_value_rtc = view.findViewById(R.id.lbl_value_rtc);
        lbl_value_ourMac = view.findViewById(R.id.lbl_value_ourmac);
        lbl_value_hostMac = view.findViewById(R.id.lbl_value_hostmac);
        lbl_value_records = view.findViewById(R.id.lbl_value_records);
        lbl_value_head = view.findViewById(R.id.lbl_value_head);
        lbl_value_tail = view.findViewById(R.id.lbl_value_tail);
        lbl_value_btmacsno = view.findViewById(R.id.lbl_value_btmacsno);

        lbl_value_gps_antenna = view.findViewById(R.id.lbl_value_gps_antenna);
        lbl_value_gps_satsno = view.findViewById(R.id.lbl_value_gps_satsno);
        lbl_value_gps_pdop = view.findViewById(R.id.lbl_value_gps_pdop);
        lbl_value_gps_time = view.findViewById(R.id.lbl_value_gps_time);
        lbl_value_gps_altitude = view.findViewById(R.id.lbl_value_gps_altitude);
        lbl_value_gps_latitude = view.findViewById(R.id.lbl_value_gps_latitude);
        lbl_value_gps_longitude = view.findViewById(R.id.lbl_value_gps_longitude);


        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX مدل " : "");

        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        Button btn_view_log = view.findViewById(R.id.btn_view_log);
        Button btn_view_card = view.findViewById(R.id.btn_view_card);

        btn_view_log.setOnClickListener(v -> {
            layout_log.setVisibility(View.VISIBLE);
            btn_view_log.setVisibility(View.INVISIBLE);
        });
        btn_view_card.setOnClickListener(v -> {
            layout_log.setVisibility(View.INVISIBLE);
            btn_view_log.setVisibility(View.VISIBLE);
        });

        Button btn_param = view.findViewById(R.id.btn_param);
        btn_param.setOnClickListener(v -> send("PARAM"));

        Button btn_gps = view.findViewById(R.id.btn_gps);
        btn_gps.setOnClickListener(v -> send("GPS"));

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("در حال اتصال ...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "عدم ارتباط", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {

        layout_display.setVisibility(View.VISIBLE);
        layout_loading.setVisibility(View.GONE);

        SpannableStringBuilder spn = new SpannableStringBuilder();
        for (byte[] data : datas) {
            if (hexEnabled) {
                spn.append(TextUtil.toHexString(data)).append('\n');
            } else {
                String msg = new String(data);
                if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                    // don't show CR as ^M if directly before LF
                    msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                    // special handling if CR and LF come in separate fragments
                    if (pendingNewline && msg.charAt(0) == '\n') {
                        if(spn.length() >= 2) {
                            spn.delete(spn.length() - 2, spn.length());
                        } else {
                            Editable edt = receiveText.getEditableText();
                            if (edt != null && edt.length() >= 2)
                                edt.delete(edt.length() - 2, edt.length());
                        }
                    }
                    pendingNewline = msg.charAt(msg.length() - 1) == '\r';
                }
                CharSequence text = TextUtil.toCaretString(msg, newline.length() != 0);
                setOnScreen(text);
                spn.append(text);
            }
        }

        receiveText.append(spn);

    }

    private void setOnScreen(CharSequence msgData){
        SpannableStringBuilder spn = new SpannableStringBuilder();
        spn.append(msgData);
        String msg = spn.toString();

//        Log.w("Ghanava " , msg);
        ArrayList<String> data = new ArrayList<String>();
        if (msg.contains(":")){
            String[] field = msg.split(":", 2);
            data.add(field[0]);
            data.add(field[1]);
        }
        else if (msg.contains("=")){
            String[] field = msg.split("=", 2);
            data.add(field[0]);
            data.add(field[1]);
        }
        if (data.size() > 0){
            String key = data.get(0).trim();
            String value = data.get(1).trim().replaceAll("\\r|\\n", "");
            switch (key) {
                case "IMEI":
                    lbl_value_imei.setText(value);
                    IMEI = value;
                    ParamsCounter++;
                    break;
                case "SBOX2":
                    lbl_value_model.setText("SBOX2");
                    lbl_value_serial.setText(value);
                    Model = "SBOX2 " + value;
                    ParamsCounter++;
                    break;
                case "TelNo":
                    lbl_value_simcard.setText(value);
                    TelNo = value;
                    ParamsCounter++;
                    break;
                case "Operator":
                    if (value.equals("1")) {
                        lbl_value_operator.setText("همراه اول");
                        Operator = "همراه اول";
                    }
                    else if (value.equals("2")) {
                        lbl_value_operator.setText("ایرانسل");
                        Operator = "ایرانسل";
                    }
                    else {
                        lbl_value_operator.setText(value);
                        Operator = value;
                    }
                    ParamsCounter++;
                    break;
                case "Firmware":
                    lbl_value_firmware.setText(value);
                    Firmware = value;
                    ParamsCounter++;
                    break;
                case "ADX":
                    lbl_value_adx.setText(value);
                    ADX = value;
                    ParamsCounter++;
                    break;
                case "SND":
                    lbl_value_snd.setText(value);
                    SND = value;
                    ParamsCounter++;
                    break;
                case "SVE":
                    lbl_value_sve.setText(value);
                    SVE = value;
                    ParamsCounter++;
                    break;
                case "MTR":
                    lbl_value_mtr.setText(value);
                    MTR = value;
                    ParamsCounter++;
                    break;
                case "DEG":
                    lbl_value_deg.setText(value);
                    DEG = value;
                    ParamsCounter++;
                    break;
                case "SPD":
                    lbl_value_spd.setText(value);
                    SPD = value;
                    ParamsCounter++;
                    break;
                case "IME":
                    lbl_value_ime.setText(value);
                    IME = value;
                    ParamsCounter++;
                    break;
                case "UPG":
                    lbl_value_upg.setText(value);
                    UPG = value;
                    ParamsCounter++;
                    break;
                case "SET":
                    lbl_value_set.setText(value);
                    SET = value;
                    ParamsCounter++;
                    break;
                case "Vin":
                    int num = Integer.parseInt(value);  ;
                    if (num < 900)
                        card_vin.setBackgroundColor(getResources().getColor(R.color.red));
                    else if(num < 1200)
                        card_vin.setBackgroundColor(getResources().getColor(R.color.orange));
                    else if(num < 3600)
                        card_vin.setBackgroundColor(getResources().getColor(R.color.green));
                    else
                        card_vin.setBackgroundColor(getResources().getColor(R.color.red));
                    lbl_value_vin.setText(value);
                    vin = value;
                    ParamsCounter++;
                    break;
                case "ACC":
                    lbl_value_acc.setText(value);
                    ACC = value;
                    ParamsCounter++;
                    break;
                case "RTC":
                    lbl_value_rtc.setText(value);
                    RTC = value;
                    ParamsCounter++;
                    break;
                case "ourMac":
                    lbl_value_ourMac.setText(value);
                    ourMac = value;
                    ParamsCounter++;
                    break;
                case "hostMac":
                    lbl_value_hostMac.setText(value);
                    hostMac = value;
                    ParamsCounter++;
                    break;
                case "Records":
                    lbl_value_records.setText(value);
                    Records = value;
                    ParamsCounter++;
                    break;
                case "Head":
                    lbl_value_head.setText(value);
                    Head = value;
                    ParamsCounter++;
                    break;
                case "Tail":
                    lbl_value_tail.setText(value);
                    Tail = value;
                    ParamsCounter++;
                    break;
                case "BtMacsNo":
                    lbl_value_btmacsno.setText(value);
                    BtMacsNo = value;
                    ParamsCounter++;
                    break;


                case "SatsNo":
                    int num2 = Integer.parseInt(value);  ;
                    if (num2 < 4)
                        card_sats.setBackgroundColor(getResources().getColor(R.color.red));
                    else if(num2 <= 8)
                        card_sats.setBackgroundColor(getResources().getColor(R.color.orange));
                    else
                        card_sats.setBackgroundColor(getResources().getColor(R.color.green));
                    lbl_value_gps_satsno.setText(value);
                    SatsNo = value;
                    ParamsCounter++;
                    break;
                case "PDOP":
                    lbl_value_gps_pdop.setText(value);
                    PDOP = value;
                    ParamsCounter++;
                    break;
                case "Latitude":
                    lbl_value_gps_latitude.setText(value);
                    Latitude = value;
                    ParamsCounter++;
                    break;
                case "Longitude":
                    lbl_value_gps_longitude.setText(value);
                    Longitude = value;
                    ParamsCounter++;
                    break;
                case "Altitude":
                    lbl_value_gps_altitude.setText(value);
                    Altitude = value;
                    ParamsCounter++;
                    break;
                case "Time":
                    lbl_value_gps_time.setText(value);
                    Time = value;
                    ParamsCounter++;
                    break;
                case "Antenna status":
                    int num3 = Integer.parseInt(value);
                    if (num3 == 0){
                        lbl_value_gps_antenna.setText("متصل است");
                        card_antenna.setBackgroundColor(getResources().getColor(R.color.green));
                    }
                    else{
                        lbl_value_gps_antenna.setText("مشکل در اتصال آنتن");
                        card_antenna.setBackgroundColor(getResources().getColor(R.color.red));
                    }
                    ParamsCounter++;
                    GPS = value;
                    break;
            }
        }

        if (ParamsCounter == 23 && !CallGPS)
        {
            CallGPS = true;
            setTimeout(this::callGPSStatus, 2000);
        }
        if (ParamsCounter == 30 && !CallPINGCloud) {
            CallPINGCloud = true;
            try {
                pingcloud();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() throws InterruptedException {
        status("اتصال برقرار شد");
        connected = Connected.True;

        send("PARAM");

        //Thread.sleep(2000); // کد را 2 ثانیه مکث کنید

       // send("GPS");

        lbl_loading.setText("ارتباط برقرار شد ، در حال دریافت وضعیت");

    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("خطای اتصال : " + e.getMessage());
        disconnect();
        Toast.makeText(getActivity(), "قطع ارتباط با دستگاه", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("ارتباط از دست رفته است : " + e.getMessage());
        Toast.makeText(getActivity(), "قطع ارتباط با دستگاه", Toast.LENGTH_SHORT).show();
        disconnect();
    }


    private void callGPSStatus(){
        Log.d("Ghanava " , "--> CALL GPS <--");
        send("GPS");
    }

    private void pingcloud() throws JSONException {
        Log.w("Ghanavati Ping" , "PingCloud Called ");
         Toast.makeText(getActivity(),"اطلاعات دریافت شد",Toast.LENGTH_SHORT).show();

        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String myUser = sharedPreferences.getString("user", "Unknown");

        Log.w("Ghanavati myUser" , myUser);
        JSONArray array = new JSONArray();

            JSONObject postedJSON = new JSONObject();
            postedJSON.put("user", myUser);
            postedJSON.put("GPS", GPS);
            postedJSON.put("IMEI", IMEI);
            postedJSON.put("Model", Model);
            postedJSON.put("TelNo", TelNo);
            postedJSON.put("vin", vin);
            postedJSON.put("Operator", Operator);
            postedJSON.put("Firmware", Firmware);
            postedJSON.put("ADX", ADX);
            postedJSON.put("SND", SND);
            postedJSON.put("SVE", SVE);
            postedJSON.put("MTR", MTR);
            postedJSON.put("DEG", DEG);
            postedJSON.put("SPD", SPD);
            postedJSON.put("IME", IME);
            postedJSON.put("UPG", UPG);
            postedJSON.put("SET", SET);
            postedJSON.put("ACC", ACC);
            postedJSON.put("RTC", RTC);
            postedJSON.put("ourMac", ourMac);
            postedJSON.put("hostMac", hostMac);
            postedJSON.put("Records", Records);
            postedJSON.put("Head", Head);
            postedJSON.put("Tail", Tail);
            postedJSON.put("BtMacsNo", BtMacsNo);
            postedJSON.put("SatsNo", SatsNo);
            postedJSON.put("PDOP", PDOP);
            postedJSON.put("Latitude", Latitude);
            postedJSON.put("Longitude", Longitude);
            postedJSON.put("Altitude", Altitude);
            postedJSON.put("Time", Time);
            array.put(postedJSON);

            JSONObject starter = new JSONObject();
            starter.put("serviceKey", "033466c08be74edc93310b3e466b1bfd");
            starter.put("data", array);

        //Log.w("Ghanavati JSON" , starter.toString());

        PostRequestTask postRequestTask = new PostRequestTask(starter);
        postRequestTask.execute();

        //reset
        clearParams();

    }

    private void clearParams(){
        IMEI = "";
        GPS = "";
        Model = "";
        TelNo = "";
        vin = "";
        Operator = "";
        Firmware = "";
        ADX = "";
        SND = "";
        SVE = "";
        MTR = "";
        DEG = "";
        SPD = "";
        IME = "";
        UPG = "";
        SET = "";
        ACC = "";
        RTC = "";
        ourMac = "";
        hostMac = "";
        Records = "";
        Head = "";
        Tail = "";
        BtMacsNo = "";
        SatsNo = "";
        PDOP = "";
        Latitude = "";
        Longitude = "";
        Altitude = "";
        Time = "";

        CallGPS = false;
        CallPINGCloud = false;
        ParamsCounter = 0;
    }

    public static void setTimeout(Runnable runnable, int delay){
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                runnable.run();
            }
            catch (Exception e){
                System.err.println(e);
            }
        }).start();
    }
}






