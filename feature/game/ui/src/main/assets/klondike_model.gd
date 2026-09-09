class_name KlondikeModel
extends RefCounted

enum Suit {
	CLUBS,
	DIAMONDS,
	HEARTS,
	SPADES,
}

const SUITS = [Suit.CLUBS, Suit.DIAMONDS, Suit.HEARTS, Suit.SPADES]

var stock: Array = []
var waste: Array = []
var foundations: Array = []
var tableau: Array = []
var selection = null
var moves := 0
var rng := RandomNumberGenerator.new()

func _init():
	reset()

func reset(seed_value = null):
	if seed_value == null:
		rng.randomize()
	else:
		rng.seed = int(seed_value)

	var deck: Array = []
	for suit in SUITS:
		for rank in range(1, 14):
			deck.append({"suit": suit, "rank": rank})
	_shuffle(deck)

	tableau = []
	var cursor := 0
	for pile_index in range(7):
		var pile: Array = []
		for card_index in range(pile_index + 1):
			pile.append({
				"card": deck[cursor],
				"face_up": card_index == pile_index,
			})
			cursor += 1
		tableau.append(pile)

	stock = deck.slice(cursor)
	waste = []
	foundations = [[], [], [], []]
	selection = null
	moves = 0

func draw_stock():
	if not stock.is_empty():
		waste.append(stock.pop_back())
		selection = null
		moves += 1
	elif not waste.is_empty():
		stock = waste.duplicate()
		stock.reverse()
		waste = []
		selection = null
		moves += 1

func _shuffle(deck: Array):
	for index in range(deck.size() - 1, 0, -1):
		var swap_index := rng.randi_range(0, index)
		var tmp = deck[index]
		deck[index] = deck[swap_index]
		deck[swap_index] = tmp
