/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.run.mail

/**
 * Smtp config
 * @param tls if tls should be used with the smtp connections
 * @param ssl if ssl should be used with the smtp connections
 * @param port the smtp port
 * @param host the smtp host name
 * @param user the smtp user
 * @param password the smtp password
 */
case class SmtpConfig(tls: Boolean = false,
                      ssl: Boolean = false,
                      port: Int = 25,
                      host: String,
                      user: String,
                      password: String)