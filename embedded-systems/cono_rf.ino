#include <RH_ASK.h>
#include <SPI.h>
#include <NewPing.h>       // Libreria per il sensore ad ultrasuoni
 

/************************ INIZIALIZZAZIONE VARIABILI SENSORE VELOCITA' *******************/
RH_ASK rf_driver(2000,11,12,10,false);

/*****************************************************************************************
************************ INIZIALIZZAZIONE VARIABILI SENSORE VELOCITA' ********************
*****************************************************************************************/

// Definizione dei pin per il sensore di distanza ad ultrasuoni
const int trigPin = 6;
const int echoPin = 7;
const unsigned int maxDistance = 200; // Massima distanza di rilevamento in cm (esempio)

long previousDistance = 0; // Memorizza la distanza precedente
unsigned long previousTime = 0; // Memorizza il tempo precedente
unsigned long previousTimeSpeedSensor = millis(); // Utilizzato per il multitasking
long timeIntervalSpeedSensor = 50;

long duration, distance;
float velocita;
unsigned long currentTime;

// Creazione dell'oggetto sensore usando la libreria NewPing
NewPing sonar(trigPin, echoPin, maxDistance);

String messaggio;
const char *messaggioChar;


void setup() {

  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);

  rf_driver.init();

  Serial.begin(9600);

}

void loop() {

      currentTime = millis(); // Ottiene il tempo corrente

    digitalWrite(trigPin, LOW);
    delayMicroseconds(2);
    digitalWrite(trigPin, HIGH);
    delayMicroseconds(10);
    digitalWrite(trigPin, LOW);

    distance = sonar.ping_cm(); // Ottiene la distanza in cm

    if(distance > 0) {
      if(previousTime > 0) { // Assicurati che non sia il primo ciclo
        // Calcola la velocità con la direzione invertita
        velocita = ((float)(previousDistance - distance) / (currentTime - previousTime)) * 1000 * 0.036;
        
        if(velocita < 0) {
        velocita = 0;
        }

        if(velocita > 150) {
        Serial.println("Velocità fuori portata");
        }
        else if( (velocita > 50) || (distance < 15) )  {
          messaggio = "pericolo_basso";
          messaggioChar = messaggio.c_str(); 
          for(int i = 0; i < 5; i++) {
            if(!rf_driver.available()) {
              rf_driver.send( (uint8_t *)messaggioChar, strlen(messaggioChar));
              if(rf_driver.waitPacketSent()) {
                Serial.println("Comando inviato");
                delay(100); 
              } 
            }
          }
        }
        else if ( (velocita > 50) && (distance < 15) ) {
          messaggio = "pericolo_medio";
          messaggioChar = messaggio.c_str(); 
          for(int i = 0; i < 5; i++) {
            if(!rf_driver.available()) {
              rf_driver.send( (uint8_t *)messaggioChar, strlen(messaggioChar));
              if(rf_driver.waitPacketSent()) {
                Serial.println("Comando inviato");
                delay(100); 
              } 
            }
          }
        }
        else {
          Serial.print(velocita, 2); // Mostra la velocità con 2 decimali
          Serial.println(" km/h    "); // Aggiungi spazi per cancellare vecchi valori più lunghi
        }
      }
      previousDistance = distance; // Aggiorna la distanza precedente
      previousTime = currentTime; // Aggiorna il tempo precedente
    } 
    else {
      // Gestione dell'errore o dell'oggetto troppo lontano/vicino
      Serial.println("oggetto non rilevato o troppo vicino");
    }
    delay(20);
}
