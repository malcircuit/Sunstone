#include "icm7218a.h"

// ICM7218A::ICM7218A(uint8_t *_data_pins, unsigned _write_pin, unsigned _mode_pin)
// {
// 	// Default starts at digit 0, RAM bank A, display off, hex decoding, and no data coming.
// 	settings = 	(RAM_BANK_A << BANK_SEL_PIN)
// 				| (HEX_DECODE << CODING_SEL_PIN);
//
// 	data_pins = _data_pins;
// 	write_pin = _write_pin;
// 	mode_pin = _mode_pin;
//
// 	DDRD |= 0xF0;	// Set pins to output
// 	DDRC |= 0xFF;
// }

ICM7218A::ICM7218A()
{
	// Default starts at digit 0, RAM bank A, display off, Code B decoding, and no data coming.
	settings = 	(RAM_BANK_A << BANK_SEL_PIN);
	
	DDRD |= 0xF0;	// Set pins to output
	DDRC |= 0xFF;
}

void ICM7218A::update_digit(uint8_t digit, unsigned decoding_type, uint8_t value, bool dp){
	this->set_decoding(decoding_type);
	this->set_digit(digit);
	
	switch (this->decode_type())
	{
		case CODE_B_DECODING:
			if (value == 0)
			{
				write_data(0x0F | (~dp << ID7));
				break;
			}
		case HEX_DECODING:
			write_data((value & 0x0F) | (~dp << ID7));
			break;
		default:
			write_data(value | (~dp << ID7));
	}
}

void ICM7218A::turn_on(){
	settings |= (1 << SHUTDOWN_PIN);
	write_control(settings);
}

void ICM7218A::turn_off(){
	settings &= ~(1 << SHUTDOWN_PIN);
	write_control(settings);
}

void ICM7218A::set_decoding(unsigned decoding_type){
	settings &= 0x9F;
	
	switch (decoding_type)
	{
		case NO_DECODING:
			settings |= (1 << DECODE_PIN);
			break;
		case HEX_DECODING:
			settings |= (1 << CODING_SEL_PIN);
			break;
		default:
			break;
	}
	
	write_control(settings);
}

unsigned ICM7218A::decode_type()
{
	switch ((settings >> DECODE_PIN) & 0x03)
	{
		case 0x00:
			return CODE_B_DECODING;
		case 0x02:
			return HEX_DECODING;
		default:
			return NO_DECODING;
	}
}

// void ICM7218A::set_colon(bool is_colon){
//
// }

void ICM7218A::set_digit(uint8_t digit)
{
	settings = (settings & 0xF8) | (digit & 0x07);
	write_control(settings);
}

void ICM7218A::write_data(uint8_t data){
	PORTC &= ~(1 << MODE_PIN);				// Send MODE low
	PORTC = (data & 0x0F) | (PORTC & 0xF0);	// Set the LSB of the input
	PORTD = (data & 0xF0) | (PORTD & 0x0F);	// Set the MSB of the input
	_NOP();									// Wait >50ns
	PORTC &= ~(1 << WRITE_PIN);				// Send WRITE low
	_NOP();									// Wait >250ns
	_NOP();
	_NOP();
	_NOP();
	_NOP();
	PORTC |= (1 << WRITE_PIN);				// Send WRITE high
}

void ICM7218A::write_control(uint8_t command){
	PORTC |= (1 << MODE_PIN);					// Send MODE high
	PORTC = (command & 0x0F) | (PORTC & 0xF0);	// Set the LSB of the input
	PORTD = (command & 0xF0) | (PORTD & 0x0F);	// Set the MSB of the input
	_NOP();										// Wait >50ns
	PORTC &= ~(1 << WRITE_PIN);					// Send WRITE low
	_NOP();										// Wait >250ns
	_NOP();
	_NOP();
	_NOP();
	_NOP();
	PORTC |= (1 << WRITE_PIN);					// Send WRITE high
}
