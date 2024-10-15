package de.kai_morich.simple_usb_terminal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
import com.hoho.android.usbserial.driver.SerialTimeoutException;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumMap;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private EnumMap<Condition, Boolean> conditionMap; // Mappa per memorizzare lo stato di ciascuna condizione

    // Enumerazione per le condizioni
    private enum Condition {
        CHECK_CASO_1,
        CHECK_CASO_2,
        CHECK_CASO_3,
        CHECK_STATO
    }

    // Stati della connessione USB
    private enum Connected { False, Pending, True }

    private final BroadcastReceiver broadcastReceiver; // Ricevitore delle trasmissioni broadcast

    // Variabili per il dispositivo USB e la porta seriale
    private int deviceId, portNum, baudRate;
    private UsbSerialPort usbSerialPort;
    private SerialService service; // Servizio per la comunicazione seriale

    // Elementi dell'interfaccia utente
    private TextView receiveText;
    private TextView sendText;
    private ControlLines controlLines;
    private TextUtil.HexWatcher hexWatcher;

    // Stati e flag
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean controlLinesEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf; // Sequenza di newline predefinita

    private Button btnLivelloBasso;
    Boolean pericolo_basso_ack;

    private Button btnLivelloMedio;
    private Button btnLivelloAlto;
    private Button btnStato;
    private Button btnBatteria;


    private LottieAnimationView feedback_1_animation;
    private LottieAnimationView feedback_2_animation;
    private LottieAnimationView feedback_3_animation;
    private LottieAnimationView feedback_4_animation;
    private LottieAnimationView feedback_5_animation;

    private LottieAnimationView status_1_animation;
    private LottieAnimationView status_2_animation;
    private LottieAnimationView status_3_animation;



    // Costruttore
    public TerminalFragment() {
        // Inizializzazione del ricevitore delle trasmissioni broadcast
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Se l'azione di intent è quella per concedere l'accesso USB
                if(Constants.INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    connect(granted); // Connessione dopo aver ottenuto il permesso
                }
            }
        };
    }

    /*
     * Ciclo di vita
     */

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // Indica che il fragment ha un menu di opzioni
        setRetainInstance(true); // Conserva l'istanza del fragment durante i cambiamenti di configurazione
        // Ottiene i parametri passati al fragment (device, port, baud)
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect(); // Disconnessione se connesso
        getActivity().stopService(new Intent(getActivity(), SerialService.class)); // Arresto del servizio
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this); // Attacca il servizio
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // Avvia il servizio se non è già stato avviato
        ContextCompat.registerReceiver(getActivity(), broadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_GRANT_USB), ContextCompat.RECEIVER_NOT_EXPORTED);
        // Registra il ricevitore delle trasmissioni broadcast per concedere l'accesso USB
    }

    @Override
    public void onStop() {
        getActivity().unregisterReceiver(broadcastReceiver); // Deregistra il ricevitore delle trasmissioni broadcast
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach(); // Se il servizio è attaccato e l'attività non sta cambiando configurazioni, distacca il servizio
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
        // Si collega al servizio
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Se è la prima volta che viene avviato e il servizio è disponibile, connettiti
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
        // Se il controllo delle linee è abilitato e connesso, avvialo
        if(controlLinesEnabled && controlLines != null && connected == Connected.True)
            controlLines.start();
    }

    @Override
    public void onPause() {
        if(controlLines != null)
            controlLines.stop(); // Ferma il controllo delle linee
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        // Se è la prima volta che viene avviato e l'attività è in stato 'resumed', connettiti
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
        View view = inflater.inflate(R.layout.fragment_terminal, container, false); // Infla il layout del fragment
        receiveText = view.findViewById(R.id.receive_text); // Ottiene il TextView per i dati ricevuti
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // Imposta il colore predefinito
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance()); // Abilita lo scrolling

        sendText = view.findViewById(R.id.send_text); // Ottiene il TextView per i dati da inviare
        hexWatcher = new TextUtil.HexWatcher(sendText); // Crea un watcher per l'inserimento di dati esadecimali
        hexWatcher.enable(hexEnabled); // Abilita il watcher
        sendText.addTextChangedListener(hexWatcher); // Aggiunge il watcher al TextView
        sendText.setHint(hexEnabled ? "HEX mode" : ""); // Imposta l'indicazione per la modalità esadecimale
        // Inizializzazione dei bottoni
        btnLivelloBasso = view.findViewById(R.id.btn_livello_basso);
        btnLivelloMedio = view.findViewById(R.id.btn_livello_medio);
        btnLivelloAlto = view.findViewById(R.id.btn_livello_alto);
        btnStato = view.findViewById(R.id.btn_funzionalità);
        btnBatteria = view.findViewById(R.id.btn_batteria);

        //inizializzazione viste per animazioni
        feedback_1_animation = view.findViewById(R.id.feedback1animation);
        feedback_2_animation = view.findViewById(R.id.feedback2animation);
        feedback_3_animation = view.findViewById(R.id.feedback3animation);
        feedback_4_animation = view.findViewById(R.id.feedback4animation);
        feedback_5_animation = view.findViewById(R.id.feedback5animation);

        status_1_animation = view.findViewById(R.id.status1animation);
        status_2_animation = view.findViewById(R.id.status2animation);
        status_3_animation = view.findViewById(R.id.status3animation);


        // Inizializzazione della mappa delle condizioni
        conditionMap = new EnumMap<>(Condition.class);
        conditionMap.put(Condition.CHECK_CASO_1, false);
        conditionMap.put(Condition.CHECK_CASO_2, false);
        conditionMap.put(Condition.CHECK_CASO_3, false);
        conditionMap.put(Condition.CHECK_STATO, false);

        View sendBtn = view.findViewById(R.id.send_btn); // Ottiene il pulsante di invio
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString())); // Imposta un listener per l'evento di clic

        // Impostazione dei listener sui bottoni

        btnLivelloBasso.setOnClickListener((v) -> {
            send("pericolo_basso");

            // Avvia un timer per controllare se ricevi l'acknoledgment entro 5 secondi
            new android.os.Handler().postDelayed(() -> {
                if (conditionMap.get(Condition.CHECK_CASO_1)) {
                    feedback_1_animation.setVisibility(View.VISIBLE);
                }
            }, 5000); // 5000 millisecondi = 5 secondi
        });

        feedback_1_animation.setVisibility(View.GONE);

        btnLivelloMedio.setOnClickListener((v -> send("pericolo_medio")));

        btnLivelloAlto.setOnClickListener((v -> send("pericolo_alto")));

        btnStato.setOnClickListener((v) -> {
            send("check_stato");
            feedback_4_animation.setAnimation(R.raw.loadinganimation);

            // Avvia un timer per controllare se ricevi l'acknoledgment entro 10 secondi
            new android.os.Handler().postDelayed(() -> {

                    if (conditionMap.get(Condition.CHECK_STATO)) {
                        feedback_4_animation.setAnimation(R.raw.checkanimation);
                        new android.os.Handler().postDelayed(() -> {
                            // Disattiva l'animazione dopo 3 secondi
                            feedback_4_animation.setAnimation(R.raw.emptyanimation);
                        }, 3000); // 3000 millisecondi = 3 secondi
                    }

                    else {
                        feedback_4_animation.setAnimation(R.raw.erroranimation);
                        new android.os.Handler().postDelayed(() -> {
                            // Disattiva l'animazione dopo 3 secondi
                            feedback_4_animation.setAnimation(R.raw.emptyanimation);
                        }, 3000); // 3000 millisecondi = 3 secondi
                    }


            }, 12000); // 12 secondi
        });

        btnBatteria.setOnClickListener((v) -> {
            send("check_batteria");
            feedback_5_animation.setAnimation(R.raw.loadinganimation);

            // Avvia un timer per controllare se ricevi l'acknoledgment entro 10 secondi
            new android.os.Handler().postDelayed(() -> {
                feedback_5_animation.setAnimation(R.raw.emptyanimation);

            }, 12000); // 12 secondi
        });



        Switch transparencySwitch = view.findViewById(R.id.switchreceiveText);
        LinearLayout sendTextLayout = view.findViewById(R.id.sendTextLayout);
        transparencySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    receiveText.setAlpha(1.0f);
                    sendTextLayout.setAlpha(1.0f);

                } else {
                    receiveText.setAlpha(0.0f);
                    sendTextLayout.setAlpha(0.0f);
                }
            }
        });



        controlLines = new ControlLines(view); // Crea e inizializza l'interfaccia per il controllo delle linee
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu); // Infla il menu delle opzioni
    }

    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        // Imposta lo stato delle opzioni nel menu
        menu.findItem(R.id.hex).setChecked(hexEnabled);
        menu.findItem(R.id.controlLines).setChecked(controlLinesEnabled);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.findItem(R.id.backgroundNotification).setChecked(service != null && service.areNotificationsEnabled());
        } else {
            menu.findItem(R.id.backgroundNotification).setChecked(true);
            menu.findItem(R.id.backgroundNotification).setEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText(""); // Cancella il testo ricevuto
            return true;
        } else if (id == R.id.newline) {
            // Imposta il tipo di newline
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
            // Abilita/disabilita la modalità esadecimale
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else if (id == R.id.controlLines) {
            // Abilita/disabilita il controllo delle linee
            controlLinesEnabled = !controlLinesEnabled;
            item.setChecked(controlLinesEnabled);
            if (controlLinesEnabled) {
                controlLines.start();
            } else {
                controlLines.stop();
            }
            return true;
        } else if (id == R.id.backgroundNotification) {
            // Imposta le notifiche in background (disponibile solo per Android Oreo e versioni successive)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!service.areNotificationsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
                } else {
                    showNotificationSettings();
                }
            }
            return true;
        } else if (id == R.id.sendBreak) {
            // Invia un break sulla porta seriale
            try {
                usbSerialPort.setBreak(true);
                Thread.sleep(100);
                status("send BREAK");
                usbSerialPort.setBreak(false);
            } catch (Exception e) {
                status("send BREAK failed: " + e.getMessage());
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */

    private void connect() {
        connect(null);
    }

    private void connect(Boolean permissionGranted) {
        // Ottiene il manager USB e cerca il dispositivo USB corrispondente
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for(UsbDevice v : usbManager.getDeviceList().values())
            if(v.getDeviceId() == deviceId)
                device = v;
        if(device == null) {
            status("connection failed: device not found");
            return;
        }
        // Prova a sondare il driver USB seriale
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if(driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            Intent intent = new Intent(Constants.INTENT_ACTION_GRANT_USB);
            intent.setPackage(getActivity().getPackageName());
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, intent, flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            usbSerialPort.open(usbConnection);
            try {
                usbSerialPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (UnsupportedOperationException e) {
                status("Setting serial parameters failed: " + e.getMessage());
            }
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
            service.connect(socket);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    // Metodi per la disconnessione e l'invio di dati
    private void disconnect() {
        connected = Connected.False;
        controlLines.stop();
        service.disconnect();
        usbSerialPort = null;
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                // Se la modalità esadecimale è abilitata, converte il testo in esadecimale
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                // Altrimenti invia il testo normale aggiungendo il newline
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn); // Aggiunge il messaggio all'area di ricezione
            service.write(data); // Invia i dati tramite il servizio
        } catch (SerialTimeoutException e) {
            status("write timeout: " + e.getMessage());
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        for (byte[] data : datas) {
            if (hexEnabled) {
                spn.append(TextUtil.toHexString(data)).append('\n'); // Se la modalità esadecimale è abilitata, visualizza i dati esadecimali
            } else {
                String msg = new String(data);

                if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                    // Se il newline è CRLF, si effettuano delle modifiche per la visualizzazione
                    msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                    if (pendingNewline && msg.charAt(0) == '\n') {
                        if (spn.length() >= 2) {
                            spn.delete(spn.length() - 2, spn.length());
                        } else {
                            Editable edt = receiveText.getEditableText();
                            if (edt != null && edt.length() >= 2)
                                edt.delete(edt.length() - 2, edt.length());
                        }
                    }
                    pendingNewline = msg.charAt(msg.length() - 1) == '\r';
                }
                spn.append(TextUtil.toCaretString(msg, newline.length() != 0)); // Mostra il testo con newline
            }
        }
        receiveText.append(spn);
            

        // Ottieni il testo completo dalla TextView
        String text = receiveText.getText().toString();
        // Dividi il testo in righe
        String[] lines = text.split("\\n"); // Usa "\\n" come delimitatore per dividere le righe
        // Estrai l'ultima riga
        String lastLine = lines[lines.length - 1];

        if (lastLine.startsWith("BATT[") && lastLine.endsWith("]")) {
            String woprefixlastLine = lastLine.substring(5);
            String cleanedlastLine = woprefixlastLine.substring(0, woprefixlastLine.length() - 1);
            controlLines.updateBatteryImages(cleanedlastLine);
        }
        else if (lastLine.equals("pericolo basso ok")) {
            conditionMap.put(Condition.CHECK_CASO_1, true);

        }
        else if (lastLine.equals("pericolo medio ok")) {
            conditionMap.put(Condition.CHECK_CASO_2, true);
        }
        else if (lastLine.equals("pericolo alto ok")) {
            conditionMap.put(Condition.CHECK_CASO_3, true);
        }

        else if(lastLine.equals("pericolo_basso")){
            showDialog("Attenzione","Pericolo basso in corso!");
        }else if(lastLine.equals("pericolo_medio")){
            showDialog("Attenzione","Pericolo medio in corso!");
        }else if(lastLine.equals("pericolo_alto")){
            showDialog("Attenzione","Pericolo alto in corso!");
        }

        /*else if(lastLine.equals("check_funzionamento_ok")){
            showDialog("successo", "ho ricevuto l'ok");
            conditionMap.put(Condition.CHECK_FUNZIONAMENTO, true);
        }*/

        else if (lastLine.startsWith("CHECK[") && lastLine.endsWith("]")){

            String woprefixlastLine = lastLine.substring(6);
            String cleanedlastLine = woprefixlastLine.substring(0, woprefixlastLine.length() - 1);

            controlLines.updateBraccialiStatus(cleanedlastLine);
           // conditionMap.put(Condition.CHECK_STATO, true);
        }

    }

    // Metodo per mostrare un popup con un messaggio di avviso
    private void showDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn); // Aggiunge il messaggio di stato all'area di ricezione
    }

    /*
     * starting with Android 14, notifications are not shown in notification bar by default when App is in background
     */

    private void showNotificationSettings() {
        // Apre le impostazioni delle notifiche
        Intent intent = new Intent();
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.putExtra("android.provider.extra.APP_PACKAGE", getActivity().getPackageName());
        startActivity(intent);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(Arrays.equals(permissions, new String[]{Manifest.permission.POST_NOTIFICATIONS}) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !service.areNotificationsEnabled())
            showNotificationSettings(); // Mostra le impostazioni delle notifiche se non sono state abilitate
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected"); // Notifica la connessione
        connected = Connected.True;
        if(controlLinesEnabled)
            controlLines.start(); // Avvia il controllo delle linee se abilitato
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage()); // Notifica l'errore di connessione
        disconnect(); // Disconnette
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas); // Gestisce la ricezione dei dati
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas); // Gestisce la ricezione dei dati
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage()); // Notifica la perdita della connessione
        disconnect(); // Disconnette
    }

    // Classe per gestire le linee di controllo
    class ControlLines {
        private static final int refreshInterval = 200; // Intervallo di aggiornamento in millisecondi

        private final Handler mainLooper;
        private final Runnable runnable;
        private final LinearLayout frame;

        // Dichiarazioni aggiuntive per gli ImageView delle batterie e i TextView
        private final ImageView bracciale1Circle;
        private final ImageView bracciale2Circle;
        private final ImageView bracciale3Circle;
        private final TextView bracciale1percentage;
        private final TextView bracciale2percentage;
        private final TextView bracciale3percentage;


        ControlLines(View view) {
            mainLooper = new Handler(Looper.getMainLooper());
            runnable = this::run;

            frame = view.findViewById(R.id.controlLines); // Ottiene il layout delle linee di controllo

            // Inizializzazione degli ImageView delle batterie

            bracciale1Circle = view.findViewById(R.id.bracciale_1_circle);
            bracciale2Circle = view.findViewById(R.id.bracciale_2_circle);
            bracciale3Circle = view.findViewById(R.id.bracciale_3_circle);

            bracciale1percentage = view.findViewById(R.id.bracciale_1_percentage);
            bracciale2percentage = view.findViewById(R.id.bracciale_2_percentage);
            bracciale3percentage = view.findViewById(R.id.bracciale_3_percentage);

        }

        void updateBraccialiStatus(String braccialiStatus){
            if (braccialiStatus != null && !braccialiStatus.isEmpty()) {
                String[] braccialiStatuses = braccialiStatus.split(","); // Dividi il testo in batterie separate

                for (String status : braccialiStatuses) {
                    String[] parts = status.split(": ");

                    //showDialog("eccomi", "index: " + parts[0] + " with value: " + parts[1] + " n.parts: " + parts.length);


                    if (parts.length == 2) {
                        String braccialeIndexStr = parts[0].replaceAll("[\\D]", ""); // Estrai l'indice della batteria

                        try{
                            int braccialeIndex = Integer.parseInt(braccialeIndexStr);
                            Boolean braccialeStatus = parts[1].equals("OK");

                            //showDialog("braccialeIndex", "index:" + braccialeIndexStr + " con status: " + braccialeStatus);
                            // Aggiorna l'immagine della batteria in base allo stato di carica
                            switch (braccialeIndex) {
                                case 1:
                                    updateBraccialeIcon(status_1_animation, braccialeStatus);
                                    break;

                                case 2:
                                    updateBraccialeIcon(status_2_animation, braccialeStatus);
                                    break;

                                case 3:
                                    updateBraccialeIcon(status_3_animation, braccialeStatus);
                                    break;

                                default:
                                    break;
                            }
                        }
                        catch (Exception e) {
                            String stackTrace = Log.getStackTraceString(e);
                            showDialog("Error", "An error happened, here's a more detailed stacktrace:" + stackTrace);
                        }
                    }
                }
            }

        }

        // Metodo per aggiornare le immagini delle batterie
        void  updateBatteryImages(String batteryStatus) {

            if (batteryStatus != null && !batteryStatus.isEmpty() && batteryStatus.contains(",")) {
                String[] batteryStatuses = batteryStatus.split(","); // Dividi il testo in batterie separate

                for (String status : batteryStatuses) {
                    String[] parts = status.split(": ");
                    if (parts.length == 2 && !parts[1].equals("NO")) {
                        String batteryIndexStr = parts[0].replaceAll("[\\D]", ""); // Estrai l'indice della batteria

                        try{
                            int batteryIndex = Integer.parseInt(batteryIndexStr);
                            double batteryLevel = Double.parseDouble(parts[1]);

                            // showDialog("batteryIndex", "index:" + batteryIndexStr +" indexp: " + batteryIndexStr + " con level: " + batteryLevel + " con batterystatus:" + batteryStatus + " e statuses:" + batteryStatuses.toString() );
                            // Aggiorna l'immagine della batteria in base allo stato di carica
                            switch (batteryIndex) {
                                case 1:
                                    updateBatteryCircle(bracciale1Circle, batteryLevel, bracciale1percentage);
                                    break;

                                    case 2:
                                        updateBatteryCircle(bracciale2Circle, batteryLevel, bracciale2percentage);
                                        break;

                                        case 3:
                                        updateBatteryCircle(bracciale3Circle, batteryLevel, bracciale3percentage);
                                        break;

                                    default:
                                        break;
                            }
                        }
                        catch (Exception e) {
                            String stackTrace = Log.getStackTraceString(e);
                            showDialog("Error", "An error occurred with BatteryStatus: " + "\"" + batteryStatus +  "\"\n" + "and index: " + batteryIndexStr + " and value: " + parts[1] + "\n\nStack trace:\n" + stackTrace);
                        }
                    }
                }
            }
        }

        public class LogDialog {

            private Context context;

            public LogDialog(Context context) {
                this.context = context;
            }

            public void showLog(String title, String message) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("OK", null) // Aggiunge un pulsante "OK" per chiudere il popup
                        .show(); // Mostra il dialogo
            }
        }

        private void updateBraccialeIcon(LottieAnimationView animationView, Boolean status){
            if(status) {
                animationView.setAnimation(R.raw.checkanimation);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Imposta l'animazione emptyanimation
                        animationView.setAnimation(R.raw.emptyanimation);
                        // Avvia l'animazione emptyanimation
                        animationView.playAnimation();
                    }
                }, 5000); // Ritardo di 3000 millisecondi (3 secondi)

            }
            else {
                animationView.setAnimation(R.raw.erroranimation);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Imposta l'animazione emptyanimation
                        animationView.setAnimation(R.raw.emptyanimation);
                        // Avvia l'animazione emptyanimation
                        animationView.playAnimation();
                    }
                }, 3000); // Ritardo di 3000 millisecondi (3 secondi)
            }
        }

        // Metodo per aggiornare l'immagine della batteria in base allo stato di carica
        private void updateBatteryCircle(ImageView imageView, double batteryLevel, TextView textView) {

            // Calcola il livello del ClipDrawable in base al livello di carica della batteria
            int clipLevel = (int) (batteryLevel * 100); // Converti il livello di carica in un intervallo da 0 a 100

            // Imposta il colore dello stroke in base alle soglie specificate
            int strokeColor;
            if (batteryLevel >= 75.0) {
                strokeColor = ContextCompat.getColor(requireContext(), R.color.battery_full); // Colore per il 75% e oltre
            } else if (batteryLevel >= 50.0) {
                strokeColor = ContextCompat.getColor(requireContext(), R.color.battery_75); // Colore per il 50% - 74%
            } else if (batteryLevel >= 25.0) {
                strokeColor = ContextCompat.getColor(requireContext(), R.color.battery_50); // Colore per il 25% - 49%
            } else {
                strokeColor = ContextCompat.getColor(requireContext(), R.color.battery_low); // Colore per meno del 25%
            }

            // Crea un GradientDrawable per disegnare l'anello
            GradientDrawable batteryDrawable = new GradientDrawable();
            batteryDrawable.setShape(GradientDrawable.OVAL);
            batteryDrawable.setStroke(8, strokeColor); // Imposta lo stroke

            // Crea un ClipDrawable basato sul GradientDrawable
            ClipDrawable batteryClipDrawable = new ClipDrawable(batteryDrawable, Gravity.START, ClipDrawable.HORIZONTAL);

            // Imposta il livello del ClipDrawable
            batteryClipDrawable.setLevel(clipLevel);

            // Imposta il ClipDrawable come sorgente dell'ImageView
            imageView.setImageDrawable(batteryClipDrawable);

            // Aggiorna il testo con la percentuale di carica della batteria, includendo il simbolo percentuale
            String batteryPercentage = String.format("%.0f%%", batteryLevel);
            textView.setText(batteryPercentage);
        }




        private void toggle(View v) {
            ToggleButton btn = (ToggleButton) v;
            if (connected != Connected.True) {
                btn.setChecked(!btn.isChecked());
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show(); // Se non connesso, mostra un messaggio e riporta lo stato del pulsante
                return;
            }
        }

        // Metodo per avviare il controllo delle linee
        void start() {
            frame.setVisibility(View.VISIBLE);
            mainLooper.post(runnable); // Avvia il task di aggiornamento periodico
        }

        // Metodo per fermare il controllo delle linee
        void stop() {
            frame.setVisibility(View.GONE);
            mainLooper.removeCallbacks(runnable); // Rimuove il task di aggiornamento
        }

        // Metodo per aggiornare lo stato delle linee di controllo
        private void run() {
            if(connected != Connected.True) return;

            mainLooper.postDelayed(runnable, refreshInterval); // Riavvia il task di aggiornamento
        }
    }
}
