extends Control

const GAME_ARG_PREFIX := "--game="
const SUDOKU_SCENE := preload("res://sudoku.tscn")
const KLONDIKE_SCENE := preload("res://klondike.tscn")

static func game_key_for_user_args(args: PackedStringArray) -> String:
	for arg in args:
		if arg.begins_with(GAME_ARG_PREFIX):
			return arg.substr(GAME_ARG_PREFIX.length())
	return "sudoku"

func _ready():
	var game_key := game_key_for_user_args(OS.get_cmdline_user_args())
	var packed_scene: PackedScene
	match game_key:
		"sudoku":
			packed_scene = SUDOKU_SCENE
		"klondike":
			packed_scene = KLONDIKE_SCENE
		_:
			push_error("Unsupported embedded game: %s" % game_key)
			get_tree().quit(1)
			return

	var game_scene := packed_scene.instantiate()
	add_child(game_scene)
	print("Embedded game bootstrap: %s" % game_key)
