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

func select_waste():
	if waste.is_empty() or is_won():
		return
	_toggle_selection({"kind": "waste"})

func select_foundation(suit: int):
	if foundations[suit].is_empty() or is_won():
		return
	_toggle_selection({"kind": "foundation", "suit": suit})

func select_tableau(pile_index: int, card_index: int):
	if is_won() or pile_index < 0 or pile_index >= tableau.size():
		return
	var pile: Array = tableau[pile_index]
	if card_index < 0 or card_index >= pile.size() or not pile[card_index]["face_up"]:
		return
	_toggle_selection({"kind": "tableau", "pile": pile_index, "card": card_index})

func selected_card_count() -> int:
	var cards = _selected_cards()
	return 0 if cards == null else cards.size()

func valid_tableau_targets() -> Array:
	if selection == null:
		return []
	var moving = _selected_cards()
	if moving == null or not _is_valid_tableau_run(moving):
		return []

	var targets: Array = []
	for pile_index in range(tableau.size()):
		if selection["kind"] == "tableau" and selection["pile"] == pile_index:
			continue
		var target = null if tableau[pile_index].is_empty() else tableau[pile_index].back()
		if _can_place_on_tableau(moving[0], target):
			targets.append(pile_index)
	return targets

func valid_foundation_targets() -> Array:
	if selection == null:
		return []
	var moving = _selected_cards()
	if moving == null or moving.size() != 1:
		return []
	var card = moving[0]
	var suit: int = card["suit"]
	if card["rank"] == foundations[suit].size() + 1:
		return [suit]
	return []

func flip_tableau_top(pile_index: int):
	if is_won() or pile_index < 0 or pile_index >= tableau.size():
		return
	var pile: Array = tableau[pile_index]
	if pile.is_empty() or pile.back()["face_up"]:
		return
	pile.back()["face_up"] = true
	selection = null
	moves += 1

func move_selected_to_tableau(target_pile_index: int):
	if selection == null or target_pile_index < 0 or target_pile_index >= tableau.size():
		return
	if selection["kind"] == "tableau" and selection["pile"] == target_pile_index:
		selection = null
		return

	var moving = _selected_cards()
	if moving == null or not _is_valid_tableau_run(moving):
		return
	var target = null if tableau[target_pile_index].is_empty() else tableau[target_pile_index].back()
	if not _can_place_on_tableau(moving[0], target):
		return

	_remove_selection()
	for card in moving:
		tableau[target_pile_index].append({"card": card, "face_up": true})
	selection = null
	moves += 1

func move_selected_to_foundation(target_suit: int):
	if selection == null:
		return
	if selection["kind"] == "foundation" and selection["suit"] == target_suit:
		selection = null
		return

	var moving = _selected_cards()
	if moving == null or moving.size() != 1:
		return
	var card = moving[0]
	if card["suit"] != target_suit or card["rank"] != foundations[target_suit].size() + 1:
		return

	_remove_selection()
	foundations[target_suit].append(card)
	selection = null
	moves += 1

func is_won() -> bool:
	var total := 0
	for pile in foundations:
		total += pile.size()
	return total == 52

static func suit_is_red(suit: int) -> bool:
	return suit == Suit.DIAMONDS or suit == Suit.HEARTS

static func suit_symbol(suit: int) -> String:
	match suit:
		Suit.CLUBS:
			return "♣"
		Suit.DIAMONDS:
			return "♦"
		Suit.HEARTS:
			return "♥"
		Suit.SPADES:
			return "♠"
	return "?"

static func rank_label(rank: int) -> String:
	match rank:
		1:
			return "A"
		11:
			return "J"
		12:
			return "Q"
		13:
			return "K"
	return str(rank)

func _toggle_selection(next_selection: Dictionary):
	if _same_selection(selection, next_selection):
		selection = null
	else:
		selection = next_selection

func _same_selection(left, right) -> bool:
	if left == null or right == null or left["kind"] != right["kind"]:
		return false
	match left["kind"]:
		"waste":
			return true
		"foundation":
			return left["suit"] == right["suit"]
		"tableau":
			return left["pile"] == right["pile"] and left["card"] == right["card"]
	return false

func _selected_cards():
	if selection == null:
		return null
	match selection["kind"]:
		"waste":
			return null if waste.is_empty() else [waste.back()]
		"foundation":
			var foundation: Array = foundations[selection["suit"]]
			return null if foundation.is_empty() else [foundation.back()]
		"tableau":
			var pile: Array = tableau[selection["pile"]]
			var card_index: int = selection["card"]
			if card_index < 0 or card_index >= pile.size():
				return null
			var result: Array = []
			for index in range(card_index, pile.size()):
				if not pile[index]["face_up"]:
					return null
				result.append(pile[index]["card"])
			return result
	return null

func _is_valid_tableau_run(cards: Array) -> bool:
	for index in range(cards.size() - 1):
		var upper = cards[index]
		var lower = cards[index + 1]
		if upper["rank"] != lower["rank"] + 1:
			return false
		if suit_is_red(upper["suit"]) == suit_is_red(lower["suit"]):
			return false
	return true

func _can_place_on_tableau(card: Dictionary, target) -> bool:
	if target == null:
		return card["rank"] == 13
	if not target["face_up"]:
		return false
	var target_card = target["card"]
	return target_card["rank"] == card["rank"] + 1 and suit_is_red(target_card["suit"]) != suit_is_red(card["suit"])

func _remove_selection():
	match selection["kind"]:
		"waste":
			waste.pop_back()
		"foundation":
			foundations[selection["suit"]].pop_back()
		"tableau":
			var source: Array = tableau[selection["pile"]]
			source.resize(selection["card"])
			if not source.is_empty() and not source.back()["face_up"]:
				source.back()["face_up"] = true

func _shuffle(deck: Array):
	for index in range(deck.size() - 1, 0, -1):
		var swap_index := rng.randi_range(0, index)
		var tmp = deck[index]
		deck[index] = deck[swap_index]
		deck[swap_index] = tmp
