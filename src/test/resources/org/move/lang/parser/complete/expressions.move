module M {
    fun main() {
        1; 1u8; 1u16; 1u32; 1u64; 1u128;

        0b11;  // invalid
        0xFF;
        0x;
        0011;
        0xRR;  // invalid
        0xFFFu128;

        (1 + 1) * (1 + 1);
        (!!true + !!true) * !!false;
        1 % 2;
        1 ^ 2;

        (a * b as u64);

        *a = 1 + 2 * (3 + 5) * 4;

        a < b && b > 2;

        v1[1+1];

        has;
        schema;

        return
    }
    fun m() {
        return 1
    }
}
