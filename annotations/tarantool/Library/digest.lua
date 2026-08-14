---@meta

---# Builtin `digest` module
---
---A "digest" is a value which is held or returned by a function that has taken a string as input and has output another string.
---
---The `digest` module supports several types of cryptographic hash functions (AES, MD4, MD5, SHA-1, SHA-2, PBKDF2) as well as a checksum function (CRC32), two functions for base64, and two non-cryptographic hash functions (guava, murmur).
local digest = {}

---Returns 256-bit binary string = digest made with AES.
digest.aes256cbc = {}

---Encrypt a string with AES-256 in CBC mode.
---
---@param input string a string to encrypt
---@param key string a 32-byte binary key
---@param iv string a 16-byte binary initialization vector
---@return string # encrypted binary string
function digest.aes256cbc.encrypt(input, key, iv) end

---Decrypt a string encrypted with AES-256 in CBC mode.
---
---@param input string an encrypted binary string
---@param key string a 32-byte binary key
---@param iv string a 16-byte binary initialization vector
---@return string # decrypted string
function digest.aes256cbc.decrypt(input, key, iv) end

---Returns 128-bit binary string = digest made with MD4.
---
---@param input string a string to hash
---@return string # 128-bit binary string
function digest.md4(input) end

---Returns 32-byte string = hexadecimal of a digest calculated with md4.
---
---@param input string a string to hash
---@return string # 32-byte hexadecimal string
function digest.md4_hex(input) end

---Returns 128-bit binary string = digest made with MD5.
---
---@param input string a string to hash
---@return string # 128-bit binary string
function digest.md5(input) end

---Returns 32-byte string = hexadecimal of a digest calculated with md5.
---
---@param input string a string to hash
---@return string # 32-byte hexadecimal string
function digest.md5_hex(input) end

---Returns 160-bit binary string = digest made with SHA-1.
---
---@param input string a string to hash
---@return string # 160-bit binary string
function digest.sha1(input) end

---Returns 40-byte string = hexadecimal of a digest calculated with sha1.
---
---@param input string a string to hash
---@return string # 40-byte hexadecimal string
function digest.sha1_hex(input) end

---Returns 224-bit binary string = digest made with SHA-224.
---
---@param input string a string to hash
---@return string # 224-bit binary string
function digest.sha224(input) end

---Returns 56-byte string = hexadecimal of a digest calculated with sha224.
---
---@param input string a string to hash
---@return string # 56-byte hexadecimal string
function digest.sha224_hex(input) end

---Returns 256-bit binary string = digest made with SHA-256.
---
---@param input string a string to hash
---@return string # 256-bit binary string
function digest.sha256(input) end

---Returns 64-byte string = hexadecimal of a digest calculated with sha256.
---
---@param input string a string to hash
---@return string # 64-byte hexadecimal string
function digest.sha256_hex(input) end

---Returns 384-bit binary string = digest made with SHA-384.
---
---@param input string a string to hash
---@return string # 384-bit binary string
function digest.sha384(input) end

---Returns 96-byte string = hexadecimal of a digest calculated with sha384.
---
---@param input string a string to hash
---@return string # 96-byte hexadecimal string
function digest.sha384_hex(input) end

---Returns 512-bit binary string = digest made with SHA-512.
---
---@param input string a string to hash
---@return string # 512-bit binary string
function digest.sha512(input) end

---Returns 128-byte string = hexadecimal of a digest calculated with sha512.
---
---@param input string a string to hash
---@return string # 128-byte hexadecimal string
function digest.sha512_hex(input) end

---Returns binary string = digest made with PBKDF2.
---
---For effective encryption the `iterations` value should be at least several thousand.
---
---**Note:** this function is a memory-intensive and CPU-intensive operation, and it blocks the event loop while it runs. Consider running it in a separate process.
---
---@param password string a data string, usually a password
---@param salt string a random string, usually a "salt"
---@param iterations? integer number of iterations (default: 100000)
---@param digest_len? integer digest length in bytes (default: 128)
---@return string # binary string
function digest.pbkdf2(password, salt, iterations, digest_len) end

---Returns hexadecimal of a digest calculated with PBKDF2.
---
---@param password string a data string, usually a password
---@param salt string a random string, usually a "salt"
---@param iterations? integer number of iterations (default: 100000)
---@param digest_len? integer digest length in bytes (default: 128)
---@return string # hexadecimal string
function digest.pbkdf2_hex(password, salt, iterations, digest_len) end

---@class digest.base64_opts
---@field nopad? boolean result must not include '=' for padding at the end
---@field nowrap? boolean result must not include line feed for splitting lines after 72 characters
---@field urlsafe? boolean result must not include '=' or line feed, and may contain '-' or '_' instead of '+' or '/'

---Returns base64 encoding from a regular string.
---
---The possible options are:
---* `nopad` -- result must not include '=' for padding at the end,
---* `nowrap` -- result must not include line feed for splitting lines after 72 characters,
---* `urlsafe` -- result must not include '=' or line feed, and may contain '-' or '_' instead of '+' or '/'.
---
---The options may be `true` or `false`, the default value is `false`.
---
---@param input string a string to encode
---@param opts? digest.base64_opts encoding options
---@return string # base64 encoded string
function digest.base64_encode(input, opts) end

---Returns a regular string from a base64 encoding.
---
---@param input string a base64 encoded string
---@return string # decoded string
function digest.base64_decode(input) end

---Returns array of random bytes with length = integer.
---
---@param length integer number of random bytes to generate
---@return string # string with random bytes
function digest.urandom(length) end

---Returns a number made with consistent hash.
---
---The guava function uses the [Consistent Hashing](https://en.wikipedia.org/wiki/Consistent_hashing) algorithm of the Google guava library. The first parameter should be a hash code; the second parameter should be the number of buckets; the returned value will be an integer between 0 and the number of buckets.
---
---@param state number|integer64 a hash code
---@param buckets integer the number of buckets
---@return integer # a bucket number, between 0 and `buckets - 1`
function digest.guava(state, buckets) end

---Returns update of a checksum calculated with CRC32.
---
---@param crc integer the checksum to update
---@param input string a string to process
---@return integer # updated 32-bit checksum
function digest.crc32_update(crc, input) end

---Incremental CRC32 state object created with `digest.crc32.new()`.
---
---@class digest.crc32_object
local crc32_object = {}

---Append a string to the incremental checksum calculation.
---
---@param input string a string to process
function crc32_object:update(input) end

---Return the current checksum value.
---
---@return integer # 32-bit checksum
function crc32_object:result() end

---Returns 32-bit checksum made with CRC32.
---
---The crc32 and crc32_update functions use the [Cyclic Redundancy Check](https://en.wikipedia.org/wiki/Cyclic_redundancy_check) polynomial value: `0x1EDC6F41` / `4812730177` (CRC-32C).
---
---**Note:** If it is necessary to be compatible with other checksum functions in other programming languages, ensure that the other functions use the same polynomial value.
---
---@class digest.crc32: table
---@field crc_begin integer initial value of the checksum
---@overload fun(input: string): integer
digest.crc32 = {}

---Initiate incremental CRC32 calculation.
---
---@return digest.crc32_object
function digest.crc32.new() end

---@class digest.murmur_opts
---@field seed? integer a seed for the hash (default: `digest.murmur.default_seed`)

---Incremental MurmurHash state object created with `digest.murmur.new()`.
---
---@class digest.murmur_object
local murmur_object = {}

---Append a string to the incremental hash calculation.
---
---@param input string a string to process
function murmur_object:update(input) end

---Return the current hash value.
---
---@return integer # 32-bit hash value
function murmur_object:result() end

---Returns 32-bit digest made with MurmurHash.
---
---@class digest.murmur: table
---@field default_seed integer default seed used when no seed is given
---@overload fun(input: string): integer
digest.murmur = {}

---Initiate incremental MurmurHash calculation.
---
---@param opts? digest.murmur_opts options, e.g. `{seed = 13}`
---@return digest.murmur_object
function digest.murmur.new(opts) end

---Incremental xxHash32 state object created with `digest.xxhash32.new()`.
---
---*Since 2.10.0*
---
---@class digest.xxhash32_object
local xxhash32_object = {}

---Append a string to the incremental hash calculation.
---
---@param input string a string to process
function xxhash32_object:update(input) end

---Return the current hash value.
---
---@return integer # 32-bit hash value
function xxhash32_object:result() end

---Clear the hash state, optionally setting a new seed.
---
---@param seed? integer a new seed (default: 0)
function xxhash32_object:clear(seed) end

---Returns 32-bit digest made with [xxHash](https://xxhash.com/).
---
---*Since 2.10.0*
---
---@class digest.xxhash32: table
---@overload fun(input: string, seed?: integer): integer
digest.xxhash32 = {}

---Initiate incremental xxHash32 calculation.
---
---@param seed? integer a seed for the hash (default: 0)
---@return digest.xxhash32_object
function digest.xxhash32.new(seed) end

---Incremental xxHash64 state object created with `digest.xxhash64.new()`.
---
---*Since 2.10.0*
---
---@class digest.xxhash64_object
local xxhash64_object = {}

---Append a string to the incremental hash calculation.
---
---@param input string a string to process
function xxhash64_object:update(input) end

---Return the current hash value.
---
---@return uint64_t # 64-bit hash value
function xxhash64_object:result() end

---Clear the hash state, optionally setting a new seed.
---
---@param seed? integer|integer64 a new seed (default: 0)
function xxhash64_object:clear(seed) end

---Returns 64-bit digest made with [xxHash](https://xxhash.com/).
---
---*Since 2.10.0*
---
---@class digest.xxhash64: table
---@overload fun(input: string, seed?: integer|integer64): uint64_t
digest.xxhash64 = {}

---Initiate incremental xxHash64 calculation.
---
---@param seed? integer|integer64 a seed for the hash (default: 0)
---@return digest.xxhash64_object
function digest.xxhash64.new(seed) end

return digest
