package com.sotti.kindergarten.config

import com.sotti.kindergarten.entity.Admin
import com.sotti.kindergarten.entity.AdminRole
import com.sotti.kindergarten.repository.AdminRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AdminDataInitializer(
    private val adminRepository: AdminRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${admin.initial.email:admin@kindergarten.com}") private val initialEmail: String,
    @Value("\${admin.initial.password}") private val initialPassword: String,
) : CommandLineRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        if (adminRepository.count() == 0L) {
            val admin =
                Admin(
                    email = initialEmail,
                    password = passwordEncoder.encode(initialPassword),
                    name = "Super Admin",
                    role = AdminRole.SUPER_ADMIN,
                )
            adminRepository.save(admin)
            log.info("Initial SUPER_ADMIN account created: $initialEmail")
        }
    }
}
