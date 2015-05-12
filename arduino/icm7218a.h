#include <stdint.h>
#include <avr/cpufunc.h>
#include <avr/io.h>
#include <util/delay.h>
//#include "pins_arduino.h"

#define DATA_INPUT 0
#define CONTROL 1
#define WRITE 0
#define NO_WRITE 1
#define RAM_BANK_A 1
#define RAM_BANK_B 0
#define OPERATING 1
#define SHUTDOWN 0
#define DECODE 0
#define NO_DECODE 1
#define HEX_DECODE 1
#define CODE_B_DECODE 0
#define DATA_COMING 1
#define NO_DATA_COMING 0

#define NO_DECODING 0
#define HEX_DECODING 1
#define CODE_B_DECODING 2

#define DIGIT_PIN_0 0
#define DIGIT_PIN_1 1
#define DIGIT_PIN_2 2
#define BANK_SEL_PIN 3
#define SHUTDOWN_PIN 4
#define DECODE_PIN 5
#define CODING_SEL_PIN 6
#define DATA_COMING_PIN 7

#define WRITE_PIN 4
#define MODE_PIN 5

#define ID0 0
#define ID1 1
#define ID2 2
#define ID3 3
#define ID4 4
#define ID5 5
#define ID6 6
#define ID7 7

class ICM7218A
{
public:
	// ICM7218A(uint8_t *_data_pins, unsigned _write_pin, unsigned _mode_pin);
	ICM7218A();
	
	void update_digit(uint8_t digit, unsigned decoding_type, uint8_t value, bool dp);
	void turn_on();
	void turn_off();
	//void set_colon(bool is_colon);
	unsigned decode_type();

private:
	uint8_t settings;
	//uint8_t data_pins[8];
	//uint8_t write_pin, mode_pin;
	void set_digit(uint8_t digit);
	void set_decoding(unsigned decoding_type);
	void write_data(uint8_t data);
	void write_control(uint8_t command);
};
