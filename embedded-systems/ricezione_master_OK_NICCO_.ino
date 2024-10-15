#include <RH_ASK.h>
#include <SPI.h>
 
RH_ASK rf_driver(2000,11,12,10,false);

String ID;
String testo_messaggio_str;
String testo_messaggio_batt;

char testo_messaggio[60];
uint8_t testo_messaggioLength = sizeof(testo_messaggio);

char msg_ricevuto[60];
String msg_ricevutoString;
uint8_t msg_ricevutoLength;
    
String statoBatterie[] = {" ", " "," "};
String statoBracciali[] = {" ", " "," "};
char* statoBraccialiChar;
int n = 3; //numero di bracciali

unsigned long currentTime;
unsigned long previousTime;
unsigned long previousTimeBLE;
unsigned long previousTimeRx;
unsigned long previousTimeRxInt;
unsigned long previousIntervalStatoBracciali;
unsigned long previousIntervalCheckBatterie;
unsigned long previousCheckBatterie;
unsigned long previousCheckStatoBracciali;

long timeIntervalInt = 100;
long timeIntervalBLE = 10;
long timeIntervalcheckBatterie = 1000000;  // intervallo check_batteria ogni 5 minuti
long timeIntervalcheckStatoBracciali = 15000;  // intervallo check_stato ogni 60 minuti
long WaitInterval = 10000;                // l'intervallo massimo di attesa per la risposta dei bracciali, oltre il quale il bracciale è considerato irraggiungibile

char *command[] = { 
    "pericolo_basso", "pericolo_medio", "pericolo_alto",
    "check_stato", "check_batteria"
    };

void setup() {

  rf_driver.init();
  rf_driver.setModeRx();
  previousTimeBLE = millis();
  previousCheckBatterie = millis();
  previousCheckStatoBracciali = millis();
  Serial.begin(19200);
  Serial1.begin(9600);
}
 
void loop() {
  
  currentTime = millis();

    if(currentTime - previousTimeBLE >= timeIntervalBLE ) {
      ricezioneInvioIntefaccia();
      previousTimeBLE = currentTime;
  }

/*

  // CHECK STATO BRACCIALI OGNI ORA
  if(currentTime - previousCheckStatoBracciali >= timeIntervalcheckStatoBracciali) {
    previousCheckStatoBracciali = currentTime;
    checkStatoBracciali();
  }

  // CHECK BATTERIE OGNI 5 MINUTI
  if(currentTime - previousCheckBatterie >= timeIntervalcheckBatterie) {
    previousCheckBatterie = currentTime;
    checkBatterie();
  }

*/

  // CHECK MESSAGGI RICEVUTI DA CONO BLE OGNI 10 MILLISECONDI
  if(currentTime - previousTimeBLE >= timeIntervalBLE ) {
    ricezioneInvioBLE();
    previousTimeBLE = currentTime;
  }

}

/******************************************************************************************************/
/************************** Sezione trasmissione messaggi verso Bracciali******************************/
/******************************************************************************************************/
// questa funzione invia un messaggio tra quelli presenti in *command[]
  void inviaComando(int numero_comando) {   
    if( !rf_driver.available() ) {
      for(int i = 0; i < 2; i++ ) {
        rf_driver.send( (uint8_t *)command[numero_comando], strlen(command[numero_comando]));
        if (rf_driver.waitPacketSent()){
          Serial.println("Comando inviato");     
        }
        delay(50);
      }
    }
  }

/******************************************************************************************************/
/************************** Sezione ricezione messaggi ricevuti da Bracciali **************************/
/******************************************************************************************************/

void ricezioneMessaggi() {
  if ( rf_driver.available() ) {
    if (rf_driver.recv(msg_ricevuto, &msg_ricevutoLength) ) {
      msg_ricevuto[msg_ricevutoLength]=0;

      msg_ricevutoString = String(msg_ricevuto);
      msg_ricevutoLength = sizeof(msg_ricevuto);
      //Serial.println(msg_ricevutoString);
      
      int symbolIndex = msg_ricevutoString.indexOf("-");
      int index;

      if (symbolIndex >= 0) {

        unsigned int bufLen = 60;
        
        ID = msg_ricevutoString.substring(0, symbolIndex); // estraggo l'ID per ottenere l'indice associato all'operatore
        testo_messaggio_str = msg_ricevutoString.substring(symbolIndex + 1, msg_ricevutoString.length()+1);
        index = ID.toInt();

        int symbolIndexBatt = testo_messaggio_str.indexOf(":");
        if (symbolIndexBatt >= 0) {
          testo_messaggio_batt = testo_messaggio_str.substring(symbolIndexBatt + 1, testo_messaggio_str.length()+1);
          Serial.println(testo_messaggio_batt);
          testo_messaggio_batt.toCharArray(testo_messaggio, bufLen);
        }
        else {
          testo_messaggio_str.toCharArray(testo_messaggio, bufLen);
        }
      } 

      testo_messaggioLength = sizeof(testo_messaggio);

      if (strncmp((char*)testo_messaggio, "OK", &testo_messaggioLength) == 0) {

        statoBracciali[index-1] = String(testo_messaggio);

        Serial.print("L'ID associato all'operatore è ");
        Serial.println(ID);
        //Serial.println(index);
        Serial.print("Lo stato del bracciale è ");
        Serial.println(statoBracciali[index-1]);

      }

      else if (strncmp((char*)testo_messaggio, "SOS OPERATORE ATTIVATO", &testo_messaggioLength ) == 0 ){
        Serial.println(testo_messaggio);		// invio del messaggio di SOS all'interfaccia
      }
      else if (strncmp((char*)testo_messaggio, "pericolo_basso", &testo_messaggioLength ) == 0) {
        Serial.println(testo_messaggio);
        inviaComando(0);
      }
      else if (strncmp((char*)testo_messaggio, "pericolo_medio", &testo_messaggioLength ) == 0) {
        Serial.println(testo_messaggio);
        inviaComando(1);
      }
      else {                                     // se il messaggio inviato dal bracciale non riguarda il check del funzionamento oppure l'SOS allora si tratta del livello di batteria
        statoBatterie[index-1] = String(testo_messaggio);
        Serial.print("L'ID associato all'operatore è ");
        Serial.println(ID);
        Serial.print("Percentuale batteria: ");
        Serial.println(statoBatterie[index-1]);
      }
    }
  }
}

/******************************************************************************************************/
/************************** Sezione divisione messaggi ricevuti da Bracciali **************************/
/******************************************************************************************************/

/*
void divisioneMessaggi(String msg_ricevutoString) {
      
      int symbolIndex = msg_ricevutoString.indexOf("-");

      if (symbolIndex >= 0) {

        unsigned int bufLen = 60;
        
        ID = msg_ricevutoString.substring(0, symbolIndex); // estraggo l'ID per ottenere l'indice associato all'operatore
        testo_messaggio_str = msg_ricevutoString.substring(symbolIndex + 1, msg_ricevutoString.length()+1);
        testo_messaggio_str.toCharArray(testo_messaggio, bufLen);
        int index = ID.toInt();

      }
    }
*/

/******************************************************************************************************/
/************************** Sezione Simulazione interfaccia *******************************************/
/******************************************************************************************************/ 

void ricezioneInvioIntefaccia() {

    if (Serial.available() > 0) {
      String input = Serial.readStringUntil('\n');  //master riceve comando da interfaccia

      // Gestisci il messaggio ricevuto
      if (input.indexOf("pericolo_basso") != -1) {
          inviaComando(0);
      } else if (input.indexOf("pericolo_medio") != -1) {
          inviaComando(1);
      } else if (input.indexOf("pericolo_alto")!= -1) {
          inviaComando(2);
      } 
      else if (input.indexOf("check_stato")!= -1) {
        Serial.println(input);
        inviaComando(3);
        bool listenForChecks = true;
        if(listenForChecks) {

        String rispostaInterfaccia;
          // svuoto stato Batterie dai precedenti livelli di batteria
          for (int i = 0; i < n; i++) {
            statoBracciali[i] = "";
          }

          rf_driver.setModeRx();
          Serial.println("In ascolto..");
          previousTimeRx = millis();
          delay(10);
          currentTime = millis();
          while(currentTime - previousTimeRx <= WaitInterval) {
            currentTime = millis();
            ricezioneMessaggi();
            delay(10);
            rispostaInterfaccia = "CHECK[";
            for(int i = 0; i < n; i++) {
              if (statoBracciali[i].length() > 0) {
                String statoBracciale = "operatore " + String(i+1) + ": " + statoBracciali[i]; 
                Serial.println(statoBracciale);
                rispostaInterfaccia += statoBracciale;
              }
              else {
                String statoBracciale = "operatore " + String(i+1) + ": NO"; 
                Serial.println(statoBracciale);
                rispostaInterfaccia += statoBracciale;
              }
              if (i < n - 1) { 
                rispostaInterfaccia += ",";
              }
            }
            rispostaInterfaccia += "]";
          }
          previousTimeRx = currentTime;
          Serial.println(rispostaInterfaccia);    // invio dello stato dei bracciali all'interfaccia
          listenForChecks = false;
        }
          else {
            Serial.println("Comando non riconosciuto");
          }
        }   
  }
}

/******************************************************************************************************/
/************************** Sezione Invio comandi reali ***********************************************/
/******************************************************************************************************/ 

void ricezioneInvioBLE() {

  if (Serial1.available() > 0) {
    String input = Serial1.readStringUntil('\n');
    Serial.println(input);

    if (input == "pericolo_medio") {
        inviaComando(1);
    } 
    if (input == "pericolo_alto") {
        inviaComando(2);
    }  
  }
}  

/******************************************************************************************************/
/************************** Sezione Gestione check batterie *******************************************/
/******************************************************************************************************/ 

void checkBatterie() {

  inviaComando(4);
  bool listenForChecks = true;
  if(listenForChecks) {

   String rispostaInterfaccia;
    // svuoto stato Batterie dai precedenti livelli di batteria
    for (int i = 0; i < n; i++) {
      statoBatterie[i] = "";
    }

    rf_driver.setModeRx();
    Serial.println("In ascolto..");
    previousTimeRx = millis();
    delay(10);
    currentTime = millis();
    while(currentTime - previousTimeRx <= WaitInterval) {
      currentTime = millis();
      Serial.println(currentTime - previousTimeRx);
      ricezioneMessaggi();
      delay(10);
      rispostaInterfaccia = "CHECK[";
      for(int i = 0; i < n; i++) {
        if (statoBatterie[i].length() > 0) {
          String statoBatteria = "operatore " + String(i+1) + ": " + statoBatterie[i]; 
          Serial.println(statoBatteria);
          rispostaInterfaccia += statoBatteria;
        }
        else {
          String statoBatteria = "operatore " + String(i+1) + ": NO"; 
          Serial.println(statoBatteria);
          rispostaInterfaccia += statoBatteria;
        }
        if (i < n - 1) { 
          rispostaInterfaccia += ",";
        }
      }
      rispostaInterfaccia += "]";
    }
    previousTimeRx = currentTime;
    Serial.println(rispostaInterfaccia);    // invio dello stato dei bracciali all'interfaccia
    listenForChecks = false;
  }

}     

/******************************************************************************************************/
/************************** Sezione Gestione check stato bracciali ************************************/
/******************************************************************************************************/ 

void checkStatoBracciali() {

  inviaComando(3);
  bool listenForChecks = true;
  if(listenForChecks) {

   String rispostaInterfaccia;
    // svuoto stato Batterie dai precedenti livelli di batteria
    for (int i = 0; i < n; i++) {
      statoBracciali[i] = "";
    }

    rf_driver.setModeRx();
    Serial.println("In ascolto..");
    previousTimeRx = millis();
    delay(10);
    currentTime = millis();
    while(currentTime - previousTimeRx <= WaitInterval) {
      currentTime = millis();
      Serial.println(currentTime - previousTimeRx);
      ricezioneMessaggi();
      delay(10);
      rispostaInterfaccia = "CHECK[";
      for(int i = 0; i < n; i++) {
        if (statoBracciali[i].length() > 0) {
          String statoBracciale = "operatore " + String(i+1) + ": " + statoBracciali[i]; 
          Serial.println(statoBracciale);
          rispostaInterfaccia += statoBracciale;
        }
        else {
          String statoBracciale = "operatore " + String(i+1) + ": NO"; 
          Serial.println(statoBracciale);
          rispostaInterfaccia += statoBracciale;
        }
        if (i < n - 1) { 
          rispostaInterfaccia += ",";
        }
      }
      rispostaInterfaccia += "]";
    }
    previousTimeRx = currentTime;
    Serial.println(rispostaInterfaccia);    // invio dello stato dei bracciali all'interfaccia
    listenForChecks = false;
  }

} 

  