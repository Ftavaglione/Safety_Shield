#include <Vcc.h>
#include <RH_ASK.h>
#include <SPI.h>


/**************** Definizione variabili per scelta modalità trasmissione/ricezione ****************

   Si utilizza uno switch per passare da modalità ricevitore a modalità trasmettitore e viceversa

  1) Modalità trasmissione --> trx_enable = true
  2) Modalità ricezione --> trx_enable = flase

Modalità di default = modalità ricezione
***************************************************************************************************/

// variabile check funzionamento
bool statoCheck;
bool richiestaCheck = false;
bool invioCheck = false;
bool initCheck = false;

// variabili pulsante SOS
bool statoSOS = 0;
bool richiestaSOS = false;

// variabili check stato batteria

const float minV = 2.7;     // volt minimi
const float maxV = 5.0;     // volt massimi
const float fix = 4.63/5.0;  // rapporto tra volt misurati con il tester e volt necessari
String perc_batt_string;

Vcc tensione(fix);

/**************************************************************************************************/
/************************** Definizione pin dispositivi *******************************************/
/**************************************************************************************************/

// Oggetto associato al ricevitore
RH_ASK rf_driver(2000,11,12,10,false);

// variabili associate ai messaggi da ricevere e da inviare
char msg_ricevuto[50];
uint8_t msgLength = sizeof(msg_ricevuto);

// stringa relativa a ID del bracciale
String ID = "2-";

String messaggio_check = "OK";
String messaggio_sos = "SOS OPERATORE ATTIVATO";

String messaggio;
const char *messaggioChar;

/**************************************************************************************************/
/************************** Definizione pin dispositivi *******************************************/
/**************************************************************************************************/

int pin_buzzer = 8;
int pin_vibrazione = 7; // pin arduino da collegare a pulsante
int pin_pulsante_check = 2; // pin arduino da collegare a pulsante
int pin_sos = 9; // pin arduino da collegare a pulsante
int portarossa = 5; // porta 11 da collegare all’anodo “rosso” del modulo RGB
int portaverde = 6; // porta 10 da collegare all’anodo “verde” del modulo RGB
int portablu = 3; // porta 9 da collegare all’anodo “blu” del modulo RBG

/****************** Routine di accensione del led ******************

nelle prossime righe viene definita la routine “colore” che, 
al momento del lancio, e’ accompagnata da tre variabili 
(rosso, verde e blu) che contengono i valori dell’intensita’ luminosa, 
di volta in volta voluta, per ogni singolo led (0 = minima e 255 = massima) 

Valori per caso allarme visivo giallo
int rosso = 255;
int blu = 0;
int verde = 255;

Valori per caso allarme visivo arancione
int rosso = 255;
int blu = 0;
int verde = 119;

Valori per caso allarme visivo rosso
int rosso = 255;
int blu = 0;
int verde = 0;

/**************************************************************************************************/
/****************************** Definizione funzioni per scenari **********************************/
/**************************************************************************************************/

void led_on (unsigned char verde, unsigned char rosso, unsigned char blu)
{
 analogWrite(portarossa, rosso); //attiva il led rosso con l’intensita’ definita nella variabile rosso
 analogWrite(portablu, blu); //attiva il led blu con l’intensita’ definita nella variabile blu
 analogWrite(portaverde, verde); //attiva il led verde con l’intensita’ definita nella variabile verde
}

void led_off ()
{
 analogWrite(portarossa, 0); //attiva il led rosso con l’intensita’ definita nella variabile rosso
 analogWrite(portablu, 0); //attiva il led blu con l’intensita’ definita nella variabile blu
 analogWrite(portaverde, 0); //attiva il led verde con l’intensita’ definita nella variabile verde
}

void pericolo_basso () {
  led_on(255, 255, 0); // accensione led RGB con parametri per generare colore giallo
  delay(250);  // aspetta 0.5 secondi 
  led_off(); // spegnimento led RGB
  delay(250); // aspetta 0.5 secondi 
}

void pericolo_medio () {
  led_on(119, 255, 0); // accensione led RGB con parametri per generare colore giallo
   digitalWrite(pin_vibrazione, HIGH); // accensione motore per vibrazione
  delay(250);  // aspetta 0.5 secondi 
  led_off(); // spegnimento led RGB
  digitalWrite(pin_vibrazione, LOW);
  delay(250); //aspetta 0.5 secondi
}

void pericolo_alto () {
 
  int freq=441;
  
  tone(pin_buzzer,freq); // accensione allarme sonoro 
  led_on(0, 255, 0); // accensione led RGB con parametri per generare colore giallo 
  digitalWrite(pin_vibrazione, HIGH); // accensione motore per vibrazione
  delay(250);  // aspetta 0.5 secondi 
  led_off(); // spegnimento led RGB
  digitalWrite(pin_vibrazione, LOW);
  noTone(pin_buzzer); // spegnimento buzzer
  delay(250);
  
}

void stato_batteria () {

  // Sezione lettura percentuale
  float percentuale_batteria = tensione.Read_Perc(minV, maxV);    // Creiamo la variabile p
  Serial.print(" Perc = ");        // Scriviamo il testo Perc
  Serial.print(percentuale_batteria);              // Scriviamo il valore di p
  Serial.println("%");            // Scriviamo il testo % 
  perc_batt_string = String(percentuale_batteria);

  messaggio = ID+"perc:"+perc_batt_string;
  messaggioChar = messaggio.c_str(); 

}

void allarme_sos() { 
  // Generazione di messaggio SOS visivo e sonoro con codice morse
  int freq=441;
  int dly=150;
    
  tone(pin_buzzer,freq);
  led_on(0, 255, 0); 
  delay(dly);
  noTone(pin_buzzer);
  led_off();
  delay(dly);
  
  tone(pin_buzzer,freq);
  led_on(0, 255, 0); 
  delay(dly);
  noTone(pin_buzzer);
  led_off();
  delay(dly);
  
  tone(pin_buzzer,freq);
  led_on(0, 255, 0); 
  delay(dly);
  noTone(pin_buzzer);
  led_off();
  delay(dly);
  
  tone(pin_buzzer,freq);
  led_on(0, 255, 0); 
  delay(3*dly);
  noTone(pin_buzzer);
  led_off();
  delay(dly);
  
  tone(pin_buzzer,freq);
  led_on(0, 255, 0); 
  delay(3*dly);
  noTone(pin_buzzer);
  led_off();
  delay(dly);
  
  tone(pin_buzzer,freq);
  led_on(0, 255, 0); 
  delay(3*dly);
  noTone(pin_buzzer);
  led_off();
  delay(dly);
 
  tone(pin_buzzer,freq);
  led_on(0, 255, 0); 
  delay(dly);
  noTone(pin_buzzer);
  led_off();
  delay(dly);
  
  tone(pin_buzzer,freq);
  led_on(0, 255, 0); 
  delay(dly);
  noTone(pin_buzzer);
  led_off();
  delay(dly);
  
  tone(pin_buzzer,freq);
  led_on(0, 255, 0); 
  delay(dly);
  noTone(pin_buzzer);
  led_off();
  delay(dly);
  
  delay(1000);
	
}
  
void check_funzionamento () {
  
  int freq=500;
  int dly=150;
  
  tone(pin_buzzer,freq);
  delay(dly);
  noTone(pin_buzzer);
  delay(dly);
  delay(500);
  
}

void trasmetti_messaggio () {
    
  for(int i = 0; i < 3; i++) {    // di default mandalo 5 volte
    rf_driver.setModeTx();
    if(!rf_driver.available()) {
      rf_driver.send( (uint8_t *)messaggioChar, strlen(messaggioChar));
      if(rf_driver.waitPacketSent()) {
        Serial.println("Comando inviato");
         Serial.println(messaggioChar);

        delay(100); 
      } 
    }
  }
  rf_driver.setModeRx();
}

void setup()
{

// Inizializzazione trasmettitore radiofrequenza  
  rf_driver.init();

 // Setup led RGB 
 pinMode(portarossa, OUTPUT); // dichiara la porta 11 come porta di output
 pinMode(portaverde, OUTPUT); // dichiara la porta 10 come porta di output
 pinMode(portablu, OUTPUT); // dichiara la porta 9 come porta di output
  
 // Setup Buzzer 
 pinMode(pin_pulsante_check, INPUT);
  
  // Setup Pulsante SOS
  pinMode(pin_sos, INPUT);
  
 // Setup Motore Vibrazione 
 pinMode(pin_vibrazione, OUTPUT);
  
 // Setup Porta Seriale per simulazione scenari
 Serial.begin(9600);
}        

void loop()
{

  //TESTING
  
  /*String msgtxt = "ao";
   rf_driver.send( (uint8_t *) msgtxt.c_str(), strlen( msgtxt.c_str()));
   delay(50);
   */
   
  
  if ( rf_driver.recv(msg_ricevuto, &msgLength)) {
    msg_ricevuto[msgLength]=0;
    if ( (strncmp((char*)msg_ricevuto, "1-OK", &msgLength) ) && (strncmp((char*)msg_ricevuto, "3-OK", &msgLength )) && (String(msg_ricevuto).indexOf(":") == -1) ) {

      Serial.println("comando scenario ricevuto");
      Serial.println(msg_ricevuto);

    /****************************************************
    ***************** Valutazione scenari ***************
    *****************************************************/
      

    /******************** Scenario 1 *********************
    Grado pericolo: Basso
    Dispositivi attivati: 
    1) avviso visivo di colore giallo  
    */  
      if ( strncmp( (char*)msg_ricevuto, "pb", &msgLength ) == 0 ) {
          pericolo_basso();
      }
      
    /******************** Scenario 2 ********************* 
    Grado pericolo: Medio 
    Dispositivi attivati: 
      1) avviso visivo di colore arancione 
      2) avviso fisico tramite motore vibrante
    */   
      if ( strncmp( (char*)msg_ricevuto, "pm", &msgLength ) == 0 ) {
          pericolo_medio();
        }
        
    /******************** Scenario 3 *********************   
    Grado pericolo: Alto 
    Dispositivi attivati: 
      1) avviso visivo di colore rosso 
      2) avviso fisico tramite motore vibrante
      3) avviso sonoro tramite buzzer 
    */ 
        
      if ( strncmp( (char*)msg_ricevuto, "pa", &msgLength ) == 0 ) {
          pericolo_alto();
        }

    /******************** Scenario 4 *********************   
    Check funzionamento bracciali 
    Dispositivi attivati: 
          3) avviso tramite buzzer 
    */ 
      if ( (strncmp( (char*)msg_ricevuto, "s", &msgLength ) == 0) ) {
      // Serial.println("scenario 4");
        if(initCheck == false) {
          richiestaCheck = true;
          while(richiestaCheck == true) {
            check_funzionamento();
            statoCheck = digitalRead(pin_pulsante_check);
            if (statoCheck == HIGH) {
              invioCheck = true;
              if(invioCheck == true) {
                messaggio = ID+messaggio_check;
                messaggioChar = messaggio.c_str();
                trasmetti_messaggio();
                invioCheck = false;
                richiestaCheck = false;
                initCheck = true;
              }
            }
          }
        }
        else {
          richiestaCheck = true;
          while(richiestaCheck == true) {
            invioCheck = true;
            if(invioCheck == true) {
              messaggio = ID+messaggio_check;
              messaggioChar = messaggio.c_str();
              trasmetti_messaggio();
              invioCheck = false;
              richiestaCheck = false;
            }
          }
        }
      }
      
    /******************** Scenario 5 *********************   
    Batteria scarica 
    Dispositivi attivati: 
          1) avviso visivo di colore rosso  
    */  
      if ( strncmp( (char*)msg_ricevuto, "b", &msgLength ) == 0 ) {
        //Serial.println("scenario 5");
          stato_batteria();
          trasmetti_messaggio();
      }
    }
  }  
  /******************** Scenario 6 *********************   
  SOS 
  Dispositivi attivati: 
        1) avviso visivo di SOS di colore rosso 
        3) avviso sonoro di SOS tramite buzzer 
  
  */
  else {
    statoSOS = digitalRead(pin_sos);
  
    if(statoSOS == HIGH) {
    richiestaSOS = true;
      if (richiestaSOS == true) {
        allarme_sos();
        messaggio = ID+messaggio_sos;
        messaggioChar = messaggio.c_str(); 
        trasmetti_messaggio();
        richiestaSOS = false;
      }
    } 
  }
}
