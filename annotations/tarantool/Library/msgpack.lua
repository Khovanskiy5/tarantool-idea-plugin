---@meta

---# Builtin `msgpack` module
---
---The `msgpack` module decodes raw [MsgPack](https://msgpack.org/) strings by converting them to Lua objects, and encodes Lua objects by converting them to raw MsgPack strings.
---
---Tarantool makes heavy internal use of MsgPack because tuples in Tarantool are stored as MsgPack arrays.
---
---Besides, starting from version 2.10.0, the `msgpack` module enables creating a specific userdata Lua object -- MsgPack object. The MsgPack object stores arbitrary MsgPack data, and can be created from any Lua object including another MsgPack object and from a raw MsgPack string. The MsgPack object has its own set of methods and iterators.
local msgpack = {}

---@class msgpack.cfg
---@field encode_max_depth? number (default: 128) Max recursion depth for encoding
---@field encode_deep_as_nil? boolean (default: false) A flag saying whether to crop tables with nesting level deeper than cfg.encode_max_depth. Not-encoded fields are replaced with one null. If not set, too deep nesting is considered an error.
---@field encode_invalid_numbers? boolean (default: true) A flag saying whether to enable encoding of NaN and Inf numbers
---@field encode_load_metatables? boolean (default: true) A flag saying whether the serializer will follow __serialize metatable field
---@field encode_use_tostring? boolean (default: false) A flag saying whether to use tostring() for unknown types
---@field encode_invalid_as_nil? boolean (default: false) A flag saying whether use NULL for non-recognized types
---@field encode_sparse_convert? boolean (default: true) A flag saying whether to handle excessively sparse arrays as maps
---@field encode_sparse_ratio? number (default: 2) 1/encode_sparse_ratio is the permissible percentage of missing values in a sparse array
---@field encode_sparse_safe? number (default: 10) A limit ensuring that small Lua arrays are always encoded as sparse arrays (instead of generating an error or encoding as a map)
---@field encode_error_as_ext? boolean (default: true) Specify how error objects are encoded in the MsgPack format
---@field decode_invalid_numbers? boolean (default: true) A flag saying whether to enable decoding of NaN and Inf numbers
---@field decode_save_metatables? boolean (default: true) A flag saying whether to set metatables for all arrays and maps
---@field decode_max_depth? number (default: 128) Max recursion depth for decoding

---Set values that affect the behavior of `msgpack.encode` and `msgpack.decode`.
---
---The values are all either integers or boolean `true`/`false`.
---
---@param cfg msgpack.cfg
function msgpack.cfg(cfg) end

---Convert a Lua object to a raw MsgPack string.
---
---Without an `ibuf` argument, returns the raw MsgPack string. With an `ibuf` argument, converts a Lua object to a raw MsgPack string in an [ibuf](doc://buffer-ibuf), which is a buffer such as `buffer.ibuf()` creates, and returns the number of bytes written.
---
---@param lua_value any either a scalar value or a Lua table value
---@return string # the original contents formatted as a raw MsgPack string
---@overload fun(lua_value: any, ibuf: buffer): integer
function msgpack.encode(lua_value) end

---Convert a raw MsgPack string to a Lua object.
---
---The variants are:
---* `msgpack.decode(msgpack_string[, start_position])` -- returns the Lua object and the number of the first byte after what was passed;
---* `msgpack.decode(C_style_string_pointer, size)` -- useful for decoding data from a buffer; returns the Lua object and a pointer to the next byte.
---
---@param msgpack_string string a raw MsgPack string
---@param start_position? integer where to start, minimum = 1, maximum = string length, default = 1
---@return any lua_value the original contents formatted as a Lua table
---@return integer next_position the number of the first byte after what was passed
---@overload fun(c_style_string_pointer: ffi.cdata*, size: integer): any, ffi.cdata*
function msgpack.decode(msgpack_string, start_position) end

---Convert a raw MsgPack string to a Lua object, without checking that the string is valid MsgPack.
---
---Because checking is skipped, `decode_unchecked()` can operate with string pointers to buffers which `decode()` cannot handle, and the pointer variant does not need a `size` argument.
---
---@param msgpack_string string a raw MsgPack string
---@param start_position? integer where to start, minimum = 1, maximum = string length, default = 1
---@return any lua_value the original contents formatted as a Lua table
---@return integer next_position the number of the first byte after what was passed
---@overload fun(c_style_string_pointer: ffi.cdata*): any, ffi.cdata*
function msgpack.decode_unchecked(msgpack_string, start_position) end

---Call the MsgPack `mp_decode_array` function and return the array size and a pointer to the first array component.
---
---A subsequent call to `msgpack_decode` can decode the component instead of the whole array.
---
---@param byte_array ffi.cdata* a pointer to a raw MsgPack string
---@param size integer a number greater than or equal to the string's length
---@return integer # the size of the array
---@return ffi.cdata* # a pointer to after the array header
function msgpack.decode_array_header(byte_array, size) end

---Call the MsgPack `mp_decode_map` function and return the map size and a pointer to the first map component.
---
---A subsequent call to `msgpack_decode` can decode the component instead of the whole map.
---
---@param byte_array ffi.cdata* a pointer to a raw MsgPack string
---@param size integer a number greater than or equal to the string's length
---@return integer # the size of the map
---@return ffi.cdata* # a pointer to after the map header
function msgpack.decode_map_header(byte_array, size) end

---A MsgPack object that stores arbitrary MsgPack data.
---
---*Since 2.10.0*
---
---To get an element of a MsgPack array or map, index the object: for an array pass an integer key (1-based), for a map pass a string key. `msgpack_object[key]` and `msgpack_object:get(key)` are equivalent.
---
---@class msgpack_object
local msgpack_object = {}

---Decode MsgPack data in the MsgPack object.
---
---@return any # a Lua object
function msgpack_object:decode() end

---Create an iterator over the MsgPack data.
---
---@return msgpack_iterator # an iterator object over the MsgPack data
function msgpack_object:iterator() end

---Get an element of the MsgPack array or map.
---
---For an array pass an integer key (1-based), for a map pass a string key. Returns `nil` if the element is missing.
---
---@param key integer|string an index of an array (1-based) or a key of a map
---@return any # the element value, or nil if it is missing
function msgpack_object:get(key) end

---An iterator over a MsgPack array or map created with `msgpack_object:iterator()`.
---
---*Since 2.10.0*
---
---@class msgpack_iterator
local msgpack_iterator = {}

---Decode a MsgPack array header under the iterator cursor and advance the cursor.
---
---After calling this function the iterator points to the first element of the array or to the value following the array if the array is empty.
---
---@return integer # number of elements in the array
function msgpack_iterator:decode_array_header() end

---Decode a MsgPack map header under the iterator cursor and advance the cursor.
---
---After calling this function the iterator points to the first key stored in the map or to the value following the map if the map is empty.
---
---@return integer # number of key-value pairs in the map
function msgpack_iterator:decode_map_header() end

---Decode a MsgPack value under the iterator cursor and advance the cursor.
---
---@return any # a Lua object corresponding to the MsgPack value
function msgpack_iterator:decode() end

---Return a MsgPack value under the iterator cursor as a MsgPack object without decoding and advance the cursor.
---
---The method doesn't copy MsgPack data. Instead, it takes a reference to the original object.
---
---@return msgpack_object # a MsgPack object
function msgpack_iterator:take() end

---Copy the specified number of MsgPack values starting from the iterator's cursor position to a new MsgPack array object and advance the cursor.
---
---*Since 2.11.0*
---
---@param count integer the number of MsgPack values to copy
---@return msgpack_object # a MsgPack object
function msgpack_iterator:take_array(count) end

---Advance the iterator cursor by skipping one MsgPack value under the cursor.
---
---Raises an error if there are no values left.
function msgpack_iterator:skip() end

---Encapsulate MsgPack data into a MsgPack object.
---
---*Since 2.10.0*
---
---@param lua_value any a Lua object of any type
---@return msgpack_object # a MsgPack object
function msgpack.object(lua_value) end

---Create a MsgPack object from a raw MsgPack string.
---
---*Since 2.10.0*
---
---@param msgpack_string string a raw MsgPack string
---@return msgpack_object # a MsgPack object
---@overload fun(c_style_string_pointer: ffi.cdata*, size: integer): msgpack_object
function msgpack.object_from_raw(msgpack_string) end

---Check if the given argument is a MsgPack object.
---
---*Since 2.10.0*
---
---@param some_argument any any argument
---@return boolean # true if the argument is a MsgPack object; otherwise, false
function msgpack.is_object(some_argument) end

---A value comparable to Lua "nil" which may be useful as a placeholder in a tuple.
msgpack.NULL = box.NULL

return msgpack
