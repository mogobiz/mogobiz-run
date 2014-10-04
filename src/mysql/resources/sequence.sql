CREATE TABLE `sequence_data` (
`sequence_name` varchar(100) NOT NULL,
`sequence_increment` int(11) unsigned NOT NULL DEFAULT 1,
`sequence_min_value` int(11) unsigned NOT NULL DEFAULT 1,
`sequence_max_value` bigint(20) unsigned NOT NULL DEFAULT 18446744073709551615,
`sequence_cur_value` bigint(20) unsigned DEFAULT 1,
`sequence_cycle` boolean NOT NULL DEFAULT TRUE,
PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO sequence_data
	(sequence_name)
VALUE
	('hibernate_sequence')
;

CREATE procedure `nextval` (IN `seq_name` varchar(100), OUT `cur_val` bigint(20))
BEGIN
    SELECT
       sequence_cur_value INTO cur_val
    FROM
       sequence_data
    WHERE
       sequence_name = seq_name
    ;

    IF cur_val IS NOT NULL THEN
        UPDATE
            sequence_data
        SET
            sequence_cur_value = IF (
                (sequence_cur_value + sequence_increment) > sequence_max_value,
                    IF (
                        sequence_cycle = TRUE,
                        sequence_min_value,
                        NULL
                    ),
                sequence_cur_value + sequence_increment
            )
        WHERE
            sequence_name = seq_name
        ;
    END IF;
END;

COMMIT;